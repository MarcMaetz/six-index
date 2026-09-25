package com.example.indexreviewer.report;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;
import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.domain.SecurityData;
import com.example.indexreviewer.review.ReviewEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Index of 2 constituents, rank 1 direct, buffer up to rank 3. Universe A–E with FFMCAP 500, 400, 300, 200,
 * 100; A and B are the current constituents.
 */
class StatusAssessmentTest {

    private static final LocalDate CUT_OFF = LocalDate.parse("2026-09-10");
    private static final LocalDate REVIEW = LocalDate.parse("2026-09-21");
    private static final ReviewPeriod PERIOD = new ReviewPeriod("P", CUT_OFF, REVIEW);
    private static final IndexDefinition INDEX =
            new IndexDefinition("TEST", "SPI", 2, 1, 3, BigDecimal.ONE, "FFMCAP", List.of(PERIOD));

    @Test
    void completedWithoutWarnings() {
        var status = assess(fullData(), List.of());

        assertThat(status.status()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(status.reasons()).isEmpty();
    }

    @Test
    void warningsWithoutDataLossDoNotNeedAttention() {
        var status = assess(fullData(), List.of(warning(Impact.NONE, "A", "B")));

        assertThat(status.status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
    }

    @Test
    void missingDataBelowBufferDoesNotNeedAttention() {
        var status = assess(fullData(), List.of(warning(Impact.MISSING_DATA, "E")));

        assertThat(status.status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
        assertThat(status.reasons()).singleElement().asString().contains("E: ranked 5, below buffer end 3");
    }

    @Test
    void missingDataOnConstituentOrWithinBufferNeedsAttention() {
        assertThat(assess(fullData(), List.of(warning(Impact.MISSING_DATA, "B"))).reasons())
                .singleElement().asString().contains("B: current constituent");
        assertThat(assess(fullData(), List.of(warning(Impact.MISSING_DATA, "C"))).reasons())
                .singleElement().asString().contains("C: ranked 3, within buffer end 3");
    }

    @Test
    void missingDataWithoutSecurityNeedsAttention() {
        var status = assess(fullData(), List.of(warning(Impact.MISSING_DATA)));

        assertThat(status.status()).isEqualTo(ReviewStatus.REQUIRES_ATTENTION);
        assertThat(status.reasons()).singleElement().asString().contains("affected security unknown");
    }

    @Test
    void unrankedSecurityIsJudgedByEstimatedRank() {
        // D has no review-date row: estimated from cut-off shares and free float, FFMCAP 200 → rank 4, harmless.
        var data = fullData();
        data.put("D", Map.of(CUT_OFF, sec("D", CUT_OFF, "2", "1", 100L)));
        var harmless = assess(data, List.of());
        assertThat(harmless.status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
        assertThat(harmless.reasons()).singleElement().asString()
                .contains("D: not ranked, estimated rank 4 is below buffer end 3");

        // Same, but big enough to reach the buffer: needs attention.
        data.put("D", Map.of(CUT_OFF, sec("D", CUT_OFF, "9", "1", 100L)));
        assertThat(assess(data, List.of()).reasons()).singleElement().asString()
                .contains("D: not ranked, estimated rank 1 is within buffer end 3");

        // No price on either date: can't estimate, so it counts as relevant.
        data.put("D", Map.of(REVIEW, sec("D", REVIEW, null, "1", 100L)));
        assertThat(assess(data, List.of()).reasons()).singleElement().asString()
                .contains("too little data to estimate its rank");
    }

    private static StatusAssessment.Result assess(Map<String, Map<LocalDate, SecurityData>> data,
                                                  List<DataQualityWarning> inputWarnings) {
        var input = new InputData(Map.of(REVIEW, Set.of("A", "B", "C", "D", "E")), data, Set.of("A", "B"),
                inputWarnings, List.of());
        return StatusAssessment.assess(new ReviewEngine().run(INDEX, PERIOD, input));
    }

    /** Price 1 on the cut-off date; shares 500, 400, … on the review date, so FFMCAP 500, 400, … */
    private static Map<String, Map<LocalDate, SecurityData>> fullData() {
        var data = new LinkedHashMap<String, Map<LocalDate, SecurityData>>();
        long shares = 500;
        for (String id : List.of("A", "B", "C", "D", "E")) {
            data.put(id, Map.of(CUT_OFF, sec(id, CUT_OFF, "1", null, null),
                    REVIEW, sec(id, REVIEW, null, "1", shares)));
            shares -= 100;
        }
        return data;
    }

    private static SecurityData sec(String id, LocalDate date, String price, String freeFloat, Long shares) {
        return new SecurityData(id, date, price == null ? null : new BigDecimal(price),
                freeFloat == null ? null : new BigDecimal(freeFloat), shares);
    }

    private static DataQualityWarning warning(Impact impact, String... ids) {
        return new DataQualityWarning("test.csv", 2, impact, "test warning", List.of(ids));
    }
}
