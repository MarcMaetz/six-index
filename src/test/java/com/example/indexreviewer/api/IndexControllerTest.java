package com.example.indexreviewer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.nio.file.Path;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against the packaged {@code config/indices.yml} and the provided data in {@code data/SMI/2026-Q3}. */
@SpringBootTest
@AutoConfigureMockMvc
class IndexControllerTest {

    @TempDir
    static Path reportsDir;

    /** Stored reports go to a temp folder, not the working directory's {@code reports/}. */
    @DynamicPropertySource
    static void reportsDir(DynamicPropertyRegistry registry) {
        registry.add("index-reviewer.reports-dir", () -> reportsDir.toString());
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    void listsConfiguredIndices() {
        assertThat(mvc.get().uri("/api/indices")).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$[0].name").isEqualTo("SMI");
                    assertThat(json).extractingPath("$[0].weightCap").isEqualTo(0.18);
                    assertThat(json).extractingPath("$[0].methodology").asString().contains("v3.40");
                    assertThat(json).extractingPath("$[0].reviewPeriods[0].cutOffDate").isEqualTo("2026-09-10");
                });
    }

    @Test
    void checksInputOfReview() {
        assertThat(mvc.get().uri("/api/indices/SMI/reviews/2026-Q3/input")).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.universeSizeOnReviewDate").isEqualTo(205);
                    assertThat(json).extractingPath("$.securityDataCountByDate['2026-09-10']").isEqualTo(205);
                    assertThat(json).extractingPath("$.securityDataCountByDate['2026-09-21']").isEqualTo(204);
                    assertThat(json).extractingPath("$.currentComposition.length()").isEqualTo(20);
                    assertThat(json).extractingPath("$.files.length()").isEqualTo(3);
                    assertThat(json).extractingPath("$.files[0].path").isEqualTo("data/SMI/2026-Q3/spi_universe.csv");
                    assertThat(json).extractingPath("$.warnings.length()").isEqualTo(1);
                });
    }

    @Test
    void returnsNotFoundForUnknownIndexOrPeriod() {
        assertThat(mvc.get().uri("/api/indices/XYZ/reviews/2026-Q3/input")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Index XYZ is not configured");
        assertThat(mvc.get().uri("/api/indices/SMI/reviews/2026-Q4/input")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Review period 2026-Q4 is not configured for SMI");
    }

    @Test
    void runsSmiQ3Review() {
        assertThat(mvc.post().uri("/api/indices/SMI/reviews/2026-Q3")).hasStatus(HttpStatus.CREATED).bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.status").isEqualTo("COMPLETED_WITH_WARNINGS");
                    assertThat(json).extractingPath("$.statusReasons[*].relevance")
                            .isEqualTo(List.of("NO_DATA_LOST", "ESTIMATED_FAR_BELOW_BUFFER"));
                    assertThat(json).extractingPath("$.constituents.length()").isEqualTo(20);
                    assertThat(json).extractingPath("$.joiners[*].securityId").isEqualTo(List.of("177"));
                    assertThat(json).extractingPath("$.leavers[*].securityId").isEqualTo(List.of("103"));
                    assertThat(json).extractingPath("$.leavers[0].reason").isEqualTo("BELOW_BUFFER");
                    assertThat(json).extractingPath("$.constituents[?(@.capped == true)].securityId")
                            .isEqualTo(List.of("155", "205"));
                    assertThat(json).extractingPath("$.constituents[0].weightPercent").isEqualTo(18.0);
                    assertThat(json).extractingPath("$.cappingRounds").isEqualTo(List.of(List.of("155", "205")));
                    assertThat(json).extractingPath("$.excluded[0].securityId").isEqualTo("166");
                    assertThat(json).extractingPath("$.parameters.methodology").asString().contains("v3.40");
                    assertThat(json).extractingPath("$.build.version").isEqualTo("0.0.1-SNAPSHOT");
                    assertThat(json).extractingPath("$.build.revision").asString().matches("[0-9a-f]{7,}(-dirty)?");
                    assertThat(json).extractingPath("$.ranking[34].price").isEqualTo(165.7);
                });
    }

    @Test
    void storesEveryRunAndReturnsItAsWritten() {
        var run = mvc.post().uri("/api/indices/SMI/reviews/2026-Q3").exchange();
        assertThat(run).hasStatus(HttpStatus.CREATED);
        String location = run.getResponse().getHeader("Location");
        assertThat(location).matches("http://localhost/api/indices/SMI/reviews/2026-Q3/reports/\\d{8}T\\d{9}Z(-\\d+)?");
        String id = location.substring(location.lastIndexOf('/') + 1);

        assertThat(mvc.get().uri(location)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.status").isEqualTo("COMPLETED_WITH_WARNINGS");
            assertThat(json).extractingPath("$.joiners[0].securityId").isEqualTo("177");
        });
        assertThat(mvc.get().uri("/api/indices/SMI/reviews/2026-Q3/reports")).hasStatusOk().bodyJson()
                .extractingPath("$[*].id").asArray().contains(id);
    }

    @Test
    void unknownStoredReportIsNotFound() {
        assertThat(mvc.get().uri("/api/indices/SMI/reviews/2026-Q3/reports/20000101T000000000Z"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.title").isEqualTo("Report not found");
        assertThat(mvc.get().uri("/api/indices/SMI/reviews/2026-Q4/reports")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void actuatorInfoShowsVersionAndRevision() {
        assertThat(mvc.get().uri("/actuator/info")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.build.version").isEqualTo("0.0.1-SNAPSHOT");
            assertThat(json).extractingPath("$.git.commit.id").asString().isNotBlank();
        });
    }

    @Test
    void reviewOfUnknownPeriodIsNotFound() {
        assertThat(mvc.post().uri("/api/indices/SMI/reviews/2026-Q4")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
