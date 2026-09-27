package com.example.indexreviewer.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.RankingStrategy;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.ingest.InputDataLoader;
import com.example.indexreviewer.review.Leaver.LeaveReason;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.review.SelectionDecision;
import com.example.indexreviewer.review.status.ReviewStatus;
import com.example.indexreviewer.review.status.StatusReason;
import com.example.indexreviewer.review.status.StatusReason.Relevance;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportBuilderTest {

    private static final ReviewPeriod Q3 =
            new ReviewPeriod("2026-Q3", LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-21"));
    private static final IndexDefinition SMI = new IndexDefinition(
            "SMI", "Rulebook v3.40", "SPI", 20, 18, 22, new BigDecimal("0.18"), RankingStrategy.FFMCAP, List.of(Q3));
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final ReviewReport.Build BUILD = new ReviewReport.Build("1.0", "abc1234-dirty");

    @Test
    void smiQ3Report() {
        var input = new InputDataLoader().load(Path.of("data/SMI/2026-Q3"), "SPI");
        var result = new ReviewEngine().run(SMI, Q3, input);

        var report = new ReportBuilder(Clock.fixed(NOW, ZoneOffset.UTC), BUILD).build(result);

        // Positional constructor with same-typed neighbours: each header field must land in its own place.
        assertThat(report.index()).isEqualTo("SMI");
        assertThat(report.reviewPeriod()).isEqualTo("2026-Q3");
        assertThat(report.cutOffDate()).isEqualTo(Q3.cutOffDate());
        assertThat(report.reviewDate()).isEqualTo(Q3.reviewDate());
        assertThat(report.generatedAt()).isEqualTo(NOW);
        assertThat(report.build()).isEqualTo(BUILD);
        assertThat(report.parameters().weightCapPercent()).isEqualByComparingTo("18");

        // Duplicate universe rows lose nothing; 166 can't be ranked, but its estimate is far below the buffer.
        assertThat(report.status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
        assertThat(report.statusReasons())
                .extracting(StatusReason::securityId, StatusReason::relevance)
                .containsExactly(
                        tuple(null, Relevance.NO_DATA_LOST), tuple("166", Relevance.ESTIMATED_FAR_BELOW_BUFFER));
        assertThat(report.statusReasons().getLast().explanation())
                .isEqualTo("Not ranked, estimated FFMCAP 12890814 "
                        + "is below half the value at buffer end rank 22 (15376002109)");

        assertThat(report.constituents()).hasSize(20).first().satisfies(c -> {
            assertThat(c.securityId()).isEqualTo("155");
            assertThat(c.weightPercent()).isEqualTo(new BigDecimal("18.000000"));
            assertThat(c.rawWeightPercent()).isEqualTo(new BigDecimal("25.417355"));
            assertThat(c.cappingFactor()).isEqualTo(new BigDecimal("0.5653526015"));
            assertThat(c.capped()).isTrue();
        });
        assertThat(report.joiners()).singleElement().satisfies(j -> {
            assertThat(j.securityId()).isEqualTo("177");
            assertThat(j.selection()).isEqualTo(SelectionDecision.SELECTED_DIRECT);
        });
        assertThat(report.leavers()).singleElement().satisfies(l -> {
            assertThat(l.securityId()).isEqualTo("103");
            assertThat(l.reason()).isEqualTo(LeaveReason.BELOW_BUFFER);
        });
        // 166 is excluded from ranking (A2); the warning is the report's only record of it.
        assertThat(report.warnings().getLast()).satisfies(w -> {
            assertThat(w.source()).isEqualTo("review");
            assertThat(w.message()).startsWith("Excluded from ranking: Missing shares on review date");
            assertThat(w.securityIds()).containsExactly("166");
        });
        assertThat(report.ranking()).hasSize(204);
        // Each FFMCAP can be recomputed from the entry: 165.7 × 45867891 × 1 for the leaver 103.
        assertThat(report.ranking())
                .filteredOn(e -> e.securityId().equals("103"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.rank()).isEqualTo(35);
                    assertThat(e.price()).isEqualByComparingTo("165.7");
                    assertThat(e.shares()).isEqualTo(45867891L);
                    assertThat(e.freeFloat()).isEqualByComparingTo("1");
                    assertThat(e.ffmcap()).isEqualByComparingTo("7600309538.70");
                });
        assertThat(report.cappingRounds()).containsExactly(List.of("155", "205"));
        assertThat(report.inputFiles()).hasSize(3);
    }
}
