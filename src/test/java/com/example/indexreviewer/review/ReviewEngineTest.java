package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.ingest.InputDataLoader;
import com.example.indexreviewer.review.Leaver.LeaveReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.indexreviewer.review.SecurityDataFixtures.securityData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

class ReviewEngineTest {

    private static final LocalDate CUT_OFF = LocalDate.parse("2026-09-10");
    private static final LocalDate REVIEW = LocalDate.parse("2026-09-21");
    private static final ReviewPeriod Q3 = new ReviewPeriod("2026-Q3", CUT_OFF, REVIEW);
    private static final IndexDefinition SMI =
            new IndexDefinition("SMI", "Rulebook v3.40", "SPI", 20, 18, 22, new BigDecimal("0.18"),
                    "FFMCAP", List.of(Q3));

    private final ReviewEngine engine = new ReviewEngine();

    @Test
    void smiQ3WithProvidedData() {
        InputData input = new InputDataLoader().load(Path.of("data/SMI/2026-Q3"), "SPI");

        ReviewResult result = engine.run(SMI, Q3, input);

        assertThat(result.constituents()).hasSize(20);
        // Ranks 1–18 directly, then the two incumbents from the buffer at 21 and 22.
        assertThat(result.constituents()).extracting(Constituent::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 21, 22);
        assertThat(result.joiners()).extracting(Constituent::securityId).containsExactly("177");
        assertThat(result.leavers()).singleElement().satisfies(leaver -> {
            assertThat(leaver.securityId()).isEqualTo("103");
            assertThat(leaver.rank()).isEqualTo(35);
            assertThat(leaver.reason()).isEqualTo(LeaveReason.BELOW_BUFFER);
            assertThat(leaver.detail()).isEqualTo("Rank 35, below the buffer");
        });
        // Buffer: 249 and 28 (new) rank above incumbents 160 and 81, which take the last two slots.
        assertThat(result.selection()).filteredOn(o -> o.rank() > 18 && o.rank() <= 22)
                .extracting(Selection.Outcome::securityId, Selection.Outcome::decision)
                .containsExactly(
                        tuple("249", SelectionDecision.NOT_SELECTED_BUFFER_FULL),
                        tuple("28", SelectionDecision.NOT_SELECTED_BUFFER_FULL),
                        tuple("160", SelectionDecision.SELECTED_BUFFER_INCUMBENT),
                        tuple("81", SelectionDecision.SELECTED_BUFFER_INCUMBENT));

        assertThat(result.constituents()).filteredOn(c -> c.weight().capped())
                .extracting(Constituent::securityId).containsExactly("155", "205");
        assertThat(result.constituents()).allSatisfy(c ->
                assertThat(c.weight().weight()).isLessThanOrEqualTo(new BigDecimal("0.18")));
        assertThat(result.constituents().stream().map(c -> c.weight().weight())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isCloseTo(BigDecimal.ONE,
                within(new BigDecimal("1E-20")));

        // 166 has no review-date data (A2): excluded with a warning, next to the duplicate-rows warning.
        assertThat(result.excluded()).extracting(Exclusion::securityId).containsExactly("166");
        assertThat(result.warnings()).hasSize(2).last().satisfies(w -> {
            assertThat(w.source()).isEqualTo("review");
            assertThat(w.securityIds()).containsExactly("166");
        });
    }

    /**
     * At 18% the Q3 data needs one capping round, so a lower cap shows why capping must repeat. After
     * capping 155 and 205 at 15%, sharing their excess lifts 63 to 15.60% and 64 above 15% too. A single pass
     * would publish 63 above the cap; the loop caps 63 and 64 in round 2.
     */
    @Test
    void capsIterativelyWhenRedistributionPushesOthersOverTheCap() {
        var cap = new BigDecimal("0.15");
        var index = new IndexDefinition("SMI", "Rulebook v3.40", "SPI", 20, 18, 22, cap, "FFMCAP", List.of(Q3));
        InputData input = new InputDataLoader().load(Path.of("data/SMI/2026-Q3"), "SPI");

        ReviewResult result = engine.run(index, Q3, input);

        assertThat(result.cappingRounds()).containsExactly(List.of("155", "205"), List.of("63", "64"));
        assertThat(result.constituents()).filteredOn(c -> c.weight().capped())
                .allSatisfy(c -> assertThat(c.weight().weight()).isEqualByComparingTo(cap));
        assertThat(result.constituents()).allSatisfy(c ->
                assertThat(c.weight().weight()).isLessThanOrEqualTo(cap));
        assertThat(result.constituents().stream().map(c -> c.weight().weight())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isCloseTo(BigDecimal.ONE, within(new BigDecimal("1E-20")));
        // 63's raw share is below the cap: only the redistribution pushes it over (A14).
        assertThat(result.constituents()).filteredOn(c -> c.securityId().equals("63")).singleElement()
                .satisfies(c -> assertThat(c.weight().rawWeight()).isLessThan(cap));
    }

    @Test
    void incumbentsLeaveWhenNotInUniverseOrNotEligible() {
        var input = new InputData(
                Map.of(REVIEW, Set.of("A", "B")),
                Map.of("A", Map.of(CUT_OFF, securityData("A", CUT_OFF, "10", null, null),
                                REVIEW, securityData("A", REVIEW, null, "1", 100L)),
                        "B", Map.of(CUT_OFF, securityData("B", CUT_OFF, "10", null, null))),
                Set.of("A", "B", "C"), List.of(), List.of());
        var index = new IndexDefinition("TEST", "Rulebook v3.40", "SPI", 1, 1, 1, BigDecimal.ONE,
                "FFMCAP", List.of(Q3));

        var result = engine.run(index, Q3, input);

        assertThat(result.constituents()).extracting(Constituent::securityId).containsExactly("A");
        assertThat(result.leavers()).extracting(Leaver::securityId, Leaver::reason).containsExactlyInAnyOrder(
                tuple("B", LeaveReason.NOT_ELIGIBLE),
                tuple("C", LeaveReason.NOT_IN_UNIVERSE));
        assertThat(result.leavers()).filteredOn(l -> l.securityId().equals("B")).singleElement()
                .satisfies(l -> assertThat(l.detail())
                        .isEqualTo("Missing shares on review date 2026-09-21, free float on review date 2026-09-21"));
    }

    @Test
    void failsWhenFewerSecuritiesAreRankedThanTheIndexNeeds() {
        // Two constituents needed, but only A can be ranked: B lacks review-date data (A12).
        var input = new InputData(
                Map.of(REVIEW, Set.of("A", "B")),
                Map.of("A", Map.of(CUT_OFF, securityData("A", CUT_OFF, "10", null, null),
                                REVIEW, securityData("A", REVIEW, null, "1", 100L)),
                        "B", Map.of(CUT_OFF, securityData("B", CUT_OFF, "10", null, null))),
                Set.of("A", "B"), List.of(), List.of());
        var index = new IndexDefinition("TEST", "Rulebook v3.40", "SPI", 2, 1, 2, BigDecimal.ONE,
                "FFMCAP", List.of(Q3));

        assertThatExceptionOfType(IncompleteIndexException.class)
                .isThrownBy(() -> engine.run(index, Q3, input))
                .withMessage("TEST needs 2 constituents, but only 1 securities could be ranked; "
                        + "1 universe securities were excluded for missing data");
    }

    @Test
    void incumbentInBufferLeavesWhenSlotsAreTaken() {
        // One constituent, direct up to rank 1, buffer to rank 2: A takes the only slot, incumbent B ranks 2nd.
        var input = new InputData(
                Map.of(REVIEW, Set.of("A", "B")),
                Map.of("A", Map.of(CUT_OFF, securityData("A", CUT_OFF, "20", null, null),
                                REVIEW, securityData("A", REVIEW, null, "1", 100L)),
                        "B", Map.of(CUT_OFF, securityData("B", CUT_OFF, "10", null, null),
                                REVIEW, securityData("B", REVIEW, null, "1", 100L))),
                Set.of("B"), List.of(), List.of());
        var index = new IndexDefinition("TEST", "Rulebook v3.40", "SPI", 1, 1, 2, BigDecimal.ONE,
                "FFMCAP", List.of(Q3));

        var result = engine.run(index, Q3, input);

        assertThat(result.leavers()).singleElement().satisfies(leaver -> {
            assertThat(leaver.securityId()).isEqualTo("B");
            assertThat(leaver.rank()).isEqualTo(2);
            assertThat(leaver.reason()).isEqualTo(LeaveReason.BUFFER_FULL);
            assertThat(leaver.detail()).isEqualTo("Rank 2, buffer slots taken");
        });
    }
}
