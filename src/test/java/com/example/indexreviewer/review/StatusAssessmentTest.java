package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;
import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.domain.SecurityData;
import com.example.indexreviewer.review.StatusReason.Relevance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.indexreviewer.review.SecurityDataFixtures.securityData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Index of 2 constituents, rank 1 direct, buffer up to rank 3. Universe A–E with FFMCAP 500, 400, 300, 200,
 * 100; A and B are the current constituents.
 */
class StatusAssessmentTest {

    private static final LocalDate CUT_OFF = LocalDate.parse("2026-09-10");
    private static final LocalDate REVIEW = LocalDate.parse("2026-09-21");
    private static final ReviewPeriod PERIOD = new ReviewPeriod("P", CUT_OFF, REVIEW);
    private static final IndexDefinition INDEX =
            new IndexDefinition("TEST", "Rulebook v3.40", "SPI", 2, 1, 3, BigDecimal.ONE, "FFMCAP", List.of(PERIOD));

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
        assertThat(status.reasons()).extracting(StatusReason::relevance).containsExactly(Relevance.NO_DATA_LOST);
    }

    @Test
    void missingDataBelowBufferDoesNotNeedAttention() {
        var status = assess(fullData(), List.of(warning(Impact.MISSING_DATA, "E")));

        assertThat(status.status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
        assertThat(status.reasons()).singleElement().satisfies(r -> {
            assertThat(r.securityId()).isEqualTo("E");
            assertThat(r.relevance()).isEqualTo(Relevance.RANKED_BELOW_BUFFER);
            assertThat(r.explanation()).isEqualTo("Ranked 5, below buffer end 3");
            assertThat(r.warning().source()).isEqualTo("test.csv");
        });
    }

    @Test
    void missingDataOnConstituentOrWithinBufferNeedsAttention() {
        var status = assess(fullData(), List.of(warning(Impact.MISSING_DATA, "B", "C", "E")));

        // All reasons are kept, harmless ones included, so the audit trail shows the whole picture.
        assertThat(status.status()).isEqualTo(ReviewStatus.REQUIRES_ATTENTION);
        assertThat(status.reasons()).extracting(StatusReason::securityId, StatusReason::relevance).containsExactly(
                tuple("B", Relevance.CURRENT_CONSTITUENT),
                tuple("C", Relevance.RANKED_WITHIN_BUFFER),
                tuple("E", Relevance.RANKED_BELOW_BUFFER));
    }

    @Test
    void missingDataWithoutSecurityNeedsAttention() {
        var status = assess(fullData(), List.of(warning(Impact.MISSING_DATA)));

        assertThat(status.status()).isEqualTo(ReviewStatus.REQUIRES_ATTENTION);
        assertThat(status.reasons()).extracting(StatusReason::relevance)
                .containsExactly(Relevance.SECURITY_UNKNOWN);
    }

    @Test
    void unrankedSecurityIsHarmlessOnlyWellBelowTheBuffer() {
        // D has no review-date row, so it is estimated from cut-off shares and free float. Without D the ranking
        // is A 500, B 400, C 300, E 100: the buffer end (rank 3) is 300, so an estimate is harmless below 150.
        var data = fullData();
        data.put("D", Map.of(CUT_OFF, securityData("D", CUT_OFF, "1", "1", 100L)));
        var harmless = assess(data, List.of());
        assertThat(harmless.status()).isEqualTo(ReviewStatus.COMPLETED_WITH_WARNINGS);
        assertThat(harmless.reasons()).singleElement().satisfies(r -> {
            assertThat(r.relevance()).isEqualTo(Relevance.ESTIMATED_FAR_BELOW_BUFFER);
            assertThat(r.explanation())
                    .isEqualTo("Not ranked, estimated FFMCAP 100 is below half the value at buffer end rank 3 (300)");
        });

        // 200 would rank 4th, below the buffer, but within the margin: the estimate is too close to call (A13).
        data.put("D", Map.of(CUT_OFF, securityData("D", CUT_OFF, "2", "1", 100L)));
        var nearBuffer = assess(data, List.of());
        assertThat(nearBuffer.status()).isEqualTo(ReviewStatus.REQUIRES_ATTENTION);
        assertThat(nearBuffer.reasons()).extracting(StatusReason::relevance)
                .containsExactly(Relevance.ESTIMATED_NEAR_BUFFER);

        // Exactly half the buffer-end value is not below it: still too close to call.
        data.put("D", Map.of(CUT_OFF, securityData("D", CUT_OFF, "1.5", "1", 100L)));
        assertThat(assess(data, List.of()).reasons()).extracting(StatusReason::relevance)
                .containsExactly(Relevance.ESTIMATED_NEAR_BUFFER);
    }

    @Test
    void unrankedSecurityWithoutPriceSharesOrFreeFloatIsNotEstimable() {
        // Any one of the three missing on both dates makes an estimate impossible, so it counts as relevant.
        var data = fullData();
        for (var row : List.of(securityData("D", REVIEW, null, "1", 100L),
                securityData("D", CUT_OFF, "1", "1", null),
                securityData("D", CUT_OFF, "1", null, 100L))) {
            data.put("D", Map.of(row.date(), row));
            assertThat(assess(data, List.of()).reasons()).extracting(StatusReason::relevance)
                    .containsExactly(Relevance.NOT_ESTIMABLE);
        }
    }

    @Test
    void bufferEndReachedExactlyGivesAThreshold() {
        // Buffer end 4 and exactly 4 ranked (A, B, C, E): the buffer end value is E's 100, so D's 1 is harmless.
        var index = new IndexDefinition("TEST", "Rulebook v3.40", "SPI", 2, 1, 4, BigDecimal.ONE,
                "FFMCAP", List.of(PERIOD));
        var data = fullData();
        data.put("D", Map.of(CUT_OFF, securityData("D", CUT_OFF, "1", "1", 1L)));

        assertThat(assess(index, data, List.of()).reasons()).extracting(StatusReason::relevance)
                .containsExactly(Relevance.ESTIMATED_FAR_BELOW_BUFFER);
    }

    @Test
    void unrankedSecurityNeedsAttentionWhenTheBufferIsNotFull() {
        // Buffer end 5, but only 4 securities can be ranked: D could take a buffer place whatever its size.
        var index = new IndexDefinition("TEST", "Rulebook v3.40", "SPI", 2, 1, 5, BigDecimal.ONE,
                "FFMCAP", List.of(PERIOD));
        var data = fullData();
        data.put("D", Map.of(CUT_OFF, securityData("D", CUT_OFF, "1", "1", 1L)));

        var status = assess(index, data, List.of());

        assertThat(status.status()).isEqualTo(ReviewStatus.REQUIRES_ATTENTION);
        assertThat(status.reasons()).extracting(StatusReason::relevance).containsExactly(Relevance.BUFFER_NOT_FULL);
    }

    @Test
    void incompleteIndexNeedsAttention() {
        // 5 constituents needed, but D can't be ranked, so only 4 are selected (A12).
        var index = new IndexDefinition("TEST", "Rulebook v3.40", "SPI", 5, 3, 5, BigDecimal.ONE,
                "FFMCAP", List.of(PERIOD));
        var data = fullData();
        data.put("D", Map.of(CUT_OFF, securityData("D", CUT_OFF, "1", "1", 1L)));

        var status = assess(index, data, List.of());

        assertThat(status.status()).isEqualTo(ReviewStatus.REQUIRES_ATTENTION);
        assertThat(status.reasons()).extracting(StatusReason::relevance)
                .containsExactly(Relevance.BUFFER_NOT_FULL, Relevance.INDEX_INCOMPLETE);
        assertThat(status.reasons().getLast().explanation()).isEqualTo("Only 4 securities could be selected, 5 needed");
        assertThat(status.reasons().getLast().warning()).isNull();
    }

    private static StatusAssessment.Result assess(Map<String, Map<LocalDate, SecurityData>> data,
                                                  List<DataQualityWarning> inputWarnings) {
        return assess(INDEX, data, inputWarnings);
    }

    private static StatusAssessment.Result assess(IndexDefinition index,
                                                  Map<String, Map<LocalDate, SecurityData>> data,
                                                  List<DataQualityWarning> inputWarnings) {
        var input = new InputData(Map.of(REVIEW, Set.of("A", "B", "C", "D", "E")), data, Set.of("A", "B"),
                inputWarnings, List.of());
        return new ReviewEngine().run(index, PERIOD, input).assessment();
    }

    /** Price 1 on the cut-off date; shares 500, 400, … on the review date, so FFMCAP 500, 400, … */
    private static Map<String, Map<LocalDate, SecurityData>> fullData() {
        var data = new LinkedHashMap<String, Map<LocalDate, SecurityData>>();
        long shares = 500;
        for (String id : List.of("A", "B", "C", "D", "E")) {
            data.put(id, Map.of(CUT_OFF, securityData(id, CUT_OFF, "1", null, null),
                    REVIEW, securityData(id, REVIEW, null, "1", shares)));
            shares -= 100;
        }
        return data;
    }


    private static DataQualityWarning warning(Impact impact, String... ids) {
        return new DataQualityWarning("test.csv", 2, impact, "test warning", List.of(ids));
    }
}
