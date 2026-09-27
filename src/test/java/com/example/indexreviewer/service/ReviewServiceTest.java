package com.example.indexreviewer.service;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.RankingStrategy;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.ingest.CsvFolderInputSource;
import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.Leaver;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.review.status.ReviewStatus;
import com.example.indexreviewer.store.FileReportStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the use cases without Spring, as a scheduler or CLI would, on the provided Q3 data. */
@ExtendWith(OutputCaptureExtension.class)
class ReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T20:10:52.184Z");

    @TempDir
    Path reportsDir;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        var q3 = new ReviewPeriod("2026-Q3", LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-21"));
        var smi = new IndexDefinition("SMI", "Rulebook v3.40", "SPI", 20, 18, 22, new BigDecimal("0.18"),
                RankingStrategy.FFMCAP, List.of(q3));
        var catalog = new IndexCatalog(List.of(smi));
        service = new ReviewService(catalog, new CsvFolderInputSource(Path.of("data")), new ReviewEngine(),
                new ReportBuilder(Clock.fixed(NOW, ZoneOffset.UTC), ReviewReport.Build.UNKNOWN),
                new FileReportStore(reportsDir, JsonMapper.builder().build()));
    }

    @Test
    void runsAndStoresReview(CapturedOutput output) {
        var run = service.run("SMI", "2026-Q3");

        assertThat(run.report().status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
        assertThat(run.report().joiners()).extracting(ReviewReport.Joiner::securityId).containsExactly("177");
        assertThat(run.report().leavers()).extracting(Leaver::securityId).containsExactly("103");
        assertThat(run.stored().id()).isEqualTo("20260925T201052184000000Z");
        assertThat(service.reports("SMI", "2026-Q3")).containsExactly(run.stored());
        assertThat(service.report("SMI", "2026-Q3", run.stored().id())).isNotEmpty();
        assertThat(output).contains(
                "Review SMI 2026-Q3 stored as report 20260925T201052184000000Z: COMPLETED_WITH_WARNINGS");
    }

    @Test
    void loadsInputWithoutRunningReview() {
        var input = service.input("SMI", "2026-Q3");

        assertThat(input.data().currentComposition()).hasSize(20);
        assertThat(service.reports("SMI", "2026-Q3")).isEmpty();
    }

    @Test
    void rejectsUnknownIndexOrPeriodBeforeStoringAnything() {
        assertThatThrownBy(() -> service.run("XYZ", "2026-Q3")).isInstanceOf(NotConfiguredException.class);
        assertThatThrownBy(() -> service.run("SMI", "2026-Q4")).isInstanceOf(NotConfiguredException.class);
        assertThat(reportsDir).isEmptyDirectory();
    }
}
