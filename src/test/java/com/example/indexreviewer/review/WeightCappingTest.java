package com.example.indexreviewer.review;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.within;

class WeightCappingTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-30");

    @Test
    void briefExample() {
        // A/B/C from the brief: FFMCAP 600/300/100, cap 50% → 50 / 37.5 / 12.5.
        var result = WeightCapping.cap(ffmcaps("A", 600, "B", 300, "C", 100), single("0.5"));

        assertThat(result.weights()).extracting(CappedWeight::weight).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.5"), new BigDecimal("0.375"), new BigDecimal("0.125"));
        assertThat(result.weights()).extracting(CappedWeight::rawWeight).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.6"), new BigDecimal("0.3"), new BigDecimal("0.1"));
        assertThat(result.weights()).extracting(CappedWeight::capped).containsExactly(true, false, false);
        assertThat(result.rounds()).containsExactly(List.of("A"));
    }

    @Test
    void cappingFactorsReproduceWeights() {
        var result = WeightCapping.cap(ffmcaps("A", 600, "B", 300, "C", 100), single("0.5"));

        // Uncapped constituents keep factor 1; A's factor scales 600 down to 400 so that A : B = 50 : 37.5.
        assertThat(result.weights()).extracting(CappedWeight::cappingFactor).satisfiesExactly(
                a -> assertThat(a).isCloseTo(new BigDecimal("0.666666666666666666666666666666"), within(TOLERANCE)),
                b -> assertThat(b).isEqualByComparingTo(BigDecimal.ONE),
                c -> assertThat(c).isEqualByComparingTo(BigDecimal.ONE));

        // FFMCAP × capping factor, normalised, gives back the final weights.
        BigDecimal adjustedTotal = result.weights().stream().map(w -> w.ffmcap().multiply(w.cappingFactor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(result.weights()).allSatisfy(w -> assertThat(w.ffmcap().multiply(w.cappingFactor())
                .divide(adjustedTotal, WeightCapping.PRECISION)).isCloseTo(w.weight(), within(TOLERANCE)));
    }

    @Test
    void repeatsWhenRedistributionPushesAnotherOverTheCap() {
        // Round 1 caps A at 40%; sharing its excess lifts B from 35% to 46.7%, so round 2 caps B too.
        var result = WeightCapping.cap(ffmcaps("A", 50, "B", 35, "C", 10, "D", 5), single("0.4"));

        assertThat(result.rounds()).containsExactly(List.of("A"), List.of("B"));
        assertThat(result.weights()).extracting(CappedWeight::weight).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.4"), new BigDecimal("0.4"),
                        new BigDecimal("0.1333333333333333333333333333333333"),
                        new BigDecimal("0.06666666666666666666666666666666667"));
    }

    @Test
    void leavesWeightsUnchangedWhenNoneExceedsCap() {
        var result = WeightCapping.cap(ffmcaps("A", 40, "B", 35, "C", 25), single("0.5"));

        assertThat(result.rounds()).isEmpty();
        assertThat(result.weights()).allSatisfy(w -> {
            assertThat(w.weight()).isEqualByComparingTo(w.rawWeight());
            assertThat(w.cappingFactor()).isEqualByComparingTo(BigDecimal.ONE);
        });
    }

    @Test
    void capsAllWhenCapTimesCountIsExactlyOne() {
        var result = WeightCapping.cap(ffmcaps("A", 60, "B", 30, "C", 10, "D", 5), single("0.25"));

        assertThat(result.weights()).allSatisfy(w -> assertThat(w.weight()).isEqualByComparingTo("0.25"));
    }

    @Test
    void noConstituentsGiveNoWeights() {
        var result = WeightCapping.cap(ffmcaps(), single("0.5"));

        assertThat(result.weights()).isEmpty();
        assertThat(result.rounds()).isEmpty();
    }

    @Test
    void invariantCheckCatchesWeightsNotAddingUpToOneOrAboveTheCap() {
        var caps = Map.of("A", new BigDecimal("0.6"), "B", new BigDecimal("0.6"));

        assertThatIllegalStateException().isThrownBy(() -> WeightCapping.checkInvariants(
                List.of(weight("A", "0.5"), weight("B", "0.4")), caps)).withMessageContaining("add up to");
        assertThatIllegalStateException().isThrownBy(() -> WeightCapping.checkInvariants(
                List.of(weight("A", "0.3"), weight("B", "0.7")), caps)).withMessageContaining("exceeds its cap");
    }

    @Test
    void rejectsCapThatCannotReachFullWeight() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WeightCapping.cap(ffmcaps("A", 1, "B", 1), single("0.4")));
    }

    @Test
    void capsEachConstituentAtItsOwnCap() {
        // Tiered rule (SLI-style): the 2 largest at 30%, the rest at 15%. Round 1 caps A (40% > 30%); sharing
        // its excess lifts C from 15% to 17.5%, above its lower cap, so round 2 caps C. B ends at 24.4%, above the
        // lower cap but within its own.
        var result = WeightCapping.cap(ffmcaps("A", 40, "B", 20, "C", 15, "D", 10, "E", 10, "F", 5),
                new TieredCap(2, new BigDecimal("0.30"), new BigDecimal("0.15")));

        assertThat(result.rounds()).containsExactly(List.of("A"), List.of("C"));
        assertThat(result.weights()).extracting(CappedWeight::capped)
                .containsExactly(true, false, true, false, false, false);
        assertThat(result.weights()).extracting(CappedWeight::weight).satisfiesExactly(
                a -> assertThat(a).isEqualByComparingTo("0.30"),
                b -> assertThat(b).isCloseTo(new BigDecimal("0.244444444444444444444444444444"), within(TOLERANCE)),
                c -> assertThat(c).isEqualByComparingTo("0.15"),
                d -> assertThat(d).isCloseTo(new BigDecimal("0.122222222222222222222222222222"), within(TOLERANCE)),
                e -> assertThat(e).isCloseTo(new BigDecimal("0.122222222222222222222222222222"), within(TOLERANCE)),
                f -> assertThat(f).isCloseTo(new BigDecimal("0.061111111111111111111111111111"), within(TOLERANCE)));
    }

    /**
     * Test-only rule proving the seam: the {@code top} largest by FFMCAP (ties by id, A8) get {@code topCap}, the
     * rest {@code otherCap}. Not configured for any index: the real SLI picks its top 4 from a half-year ranking.
     */
    private record TieredCap(int top, BigDecimal topCap, BigDecimal otherCap) implements CappingRule {
        @Override
        public Map<String, BigDecimal> caps(SequencedMap<String, BigDecimal> ffmcapById) {
            var largest = ffmcapById.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(top)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            var caps = new LinkedHashMap<String, BigDecimal>();
            ffmcapById.keySet().forEach(id -> caps.put(id, largest.contains(id) ? topCap : otherCap));
            return caps;
        }
    }

    private static CappingRule single(String cap) {
        return new SingleCap(new BigDecimal(cap));
    }

    private static SequencedMap<String, BigDecimal> ffmcaps(Object... idsAndValues) {
        var map = new LinkedHashMap<String, BigDecimal>();
        for (int i = 0; i < idsAndValues.length; i += 2) {
            map.put((String) idsAndValues[i], BigDecimal.valueOf(((Number) idsAndValues[i + 1]).longValue()));
        }
        return map;
    }

    private static CappedWeight weight(String id, String weight) {
        var value = new BigDecimal(weight);
        return new CappedWeight(id, value, value, value, BigDecimal.ONE, false);
    }
}
