package com.example.indexreviewer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against the packaged {@code config/indices.yml} and the provided data in {@code data/SMI/2026-Q3}. */
@SpringBootTest
@AutoConfigureMockMvc
class IndexControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void listsConfiguredIndices() {
        assertThat(mvc.get().uri("/api/indices")).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$[0].name").isEqualTo("SMI");
                    assertThat(json).extractingPath("$[0].weightCap").isEqualTo(0.18);
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
}
