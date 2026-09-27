package com.example.indexreviewer.store;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.ingest.InputDataLoader;
import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.review.ReviewResult;
import com.example.indexreviewer.review.status.ReviewStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileReportStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-25T20:10:52.184Z");
    private static final ReviewReport.Build BUILD = new ReviewReport.Build("1.0", "abc1234");
    private static ReviewResult result;
    private static ReviewReport report;

    @TempDir
    Path root;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeAll
    static void runQ3Review() {
        var q3 = new ReviewPeriod("2026-Q3", LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-21"));
        var smi = new IndexDefinition("SMI", "Rulebook v3.40", "SPI", 20, 18, 22, new BigDecimal("0.18"),
                "FFMCAP", List.of(q3));
        result = new ReviewEngine().run(smi, q3, new InputDataLoader().load(Path.of("data/SMI/2026-Q3"), "SPI"));
        report = withTime(NOW);
    }

    private static ReviewReport withTime(Instant generatedAt) {
        return new ReportBuilder(Clock.fixed(generatedAt, ZoneOffset.UTC), BUILD)
                .build(result);
    }

    @Test
    void storesReportAsJsonFileNamedByGenerationTime() throws IOException {
        var store = new FileReportStore(root, mapper);

        var stored = store.save(report);

        assertThat(stored.id()).isEqualTo("20260925T201052184000000Z");
        assertThat(stored.status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
        Path file = root.resolve("SMI/2026-Q3/20260925T201052184000000Z.json");
        assertThat(file).exists();
        assertThat(store.read("SMI", "2026-Q3", stored.id())).isEqualTo(Files.readAllBytes(file));

        // What was written reads back as the same report, fixed decimals included.
        var readBack = mapper.readValue(Files.readAllBytes(file), ReviewReport.class);
        assertThat(readBack).isEqualTo(report);
        assertThat(Files.readString(file)).contains("\"weightPercent\" : 18.000000");
    }

    @Test
    void neverOverwritesAnExistingReport() throws IOException {
        var store = new FileReportStore(root, mapper);
        var first = store.save(report);
        byte[] written = store.read("SMI", "2026-Q3", first.id());

        assertThatThrownBy(() -> store.save(report)).hasCauseInstanceOf(FileAlreadyExistsException.class);
        assertThat(store.read("SMI", "2026-Q3", first.id())).isEqualTo(written);
    }

    @Test
    void listsRunsInChronologicalOrder() {
        var store = new FileReportStore(root, mapper);
        for (Instant time : List.of(NOW.plusSeconds(1), NOW, NOW.plusNanos(1))) {
            store.save(withTime(time));
        }

        assertThat(store.list("SMI", "2026-Q3")).extracting(StoredReport::id).containsExactly(
                "20260925T201052184000000Z", "20260925T201052184000001Z", "20260925T201053184000000Z");
    }

    @Test
    void listIsEmptyBeforeTheFirstRun() {
        assertThat(new FileReportStore(root, mapper).list("SMI", "2026-Q3")).isEmpty();
    }

    @Test
    void failsToListAReportMissingASummaryField() throws IOException {
        var dir = Files.createDirectories(root.resolve("SMI/2026-Q3"));
        Files.writeString(dir.resolve("20260925T201052184000000Z.json"), "{\"generatedAt\" : \"2026-09-25T20:10:52.184Z\"}");

        assertThatThrownBy(() -> new FileReportStore(root, mapper).list("SMI", "2026-Q3"))
                .hasMessageContaining("status");
    }

    @Test
    void unknownOrUnsafeIdsAreNotFound() {
        var store = new FileReportStore(root, mapper);
        store.save(report);

        assertThatThrownBy(() -> store.read("SMI", "2026-Q3", "20260101T000000000Z"))
                .isInstanceOf(ReportNotFoundException.class);
        assertThatThrownBy(() -> store.read("SMI", "2026-Q3", "../../etc/passwd"))
                .isInstanceOf(ReportNotFoundException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> store.list("..", "2026-Q3"));
    }
}
