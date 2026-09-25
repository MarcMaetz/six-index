package com.example.indexreviewer.report;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.ingest.InputDataLoader;
import com.example.indexreviewer.report.StatusReason.Relevance;
import com.example.indexreviewer.review.Leaver.LeaveReason;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.review.SelectionDecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReportBuilderTest {

    private static final ReviewPeriod Q3 =
            new ReviewPeriod("2026-Q3", LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-21"));
    private static final IndexDefinition SMI =
            new IndexDefinition("SMI", "SPI", 20, 18, 22, new BigDecimal("0.18"), "FFMCAP", List.of(Q3));
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Test
    void smiQ3Report() {
        var input = new InputDataLoader().load(Path.of("data/SMI/2026-Q3"), "SPI");
        var result = new ReviewEngine().run(SMI, Q3, input);

        var report = new ReportBuilder(new ReportFormat(6, 10, 2), Clock.fixed(NOW, ZoneOffset.UTC)).build(result);

        assertThat(report.generatedAt()).isEqualTo(NOW);
        assertThat(report.parameters().weightCapPercent()).isEqualByComparingTo("18");

        // Duplicate universe rows lose nothing; 166 can't be ranked, but its estimate is far below the buffer.
        assertThat(report.status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
        assertThat(report.statusReasons()).extracting(StatusReason::securityId, StatusReason::relevance)
                .containsExactly(tuple(null, Relevance.NO_DATA_LOST), tuple("166", Relevance.ESTIMATED_FAR_BELOW_BUFFER));
        assertThat(report.statusReasons().getLast().explanation()).isEqualTo("Not ranked, estimated FFMCAP 12890814 "
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
        assertThat(report.excluded()).extracting(ReviewReport.Exclusion::securityId).containsExactly("166");
        assertThat(report.ranking()).hasSize(204);
        assertThat(report.cappingRounds()).containsExactly(List.of("155", "205"));
        assertThat(report.inputFiles()).hasSize(3);
    }
}
