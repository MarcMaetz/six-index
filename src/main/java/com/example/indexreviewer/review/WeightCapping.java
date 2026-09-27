package com.example.indexreviewer.review;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * Iterative weight capping (rulebook 5.12.4, brief's example). Each round:
 * <ol>
 *   <li>capped constituents get exactly the cap; the others share the weight that is left in proportion to their
 *       FFMCAP;</li>
 *   <li>any constituent now above the cap is capped, and the round repeats.</li>
 * </ol>
 * Sharing out a capped constituent's excess can push another one over the cap, hence the rounds (A14). With the
 * brief's example (FFMCAP 600 / 300 / 100, cap 50%): round 1 gives 60 / 30 / 10%, so A is capped; round 2 gives
 * A 50% and shares the other 50% as 3 : 1, so B 37.5% and C 12.5%. Nobody is above the cap, done.
 * <p>
 * One cap applies to all constituents. Everything is calculated at full precision; only the report rounds.
 */
final class WeightCapping {

    static final MathContext PRECISION = MathContext.DECIMAL128;
    static final BigDecimal SUM_TOLERANCE = new BigDecimal("1E-20");

    /**
     * @param weights constituents' weights, in the order given
     * @param rounds  ids capped in each round, for traceability; empty if no constituent exceeded the cap
     */
    record Result(List<CappedWeight> weights, List<List<String>> rounds) {
        Result {
            weights = List.copyOf(weights);
            rounds = rounds.stream().map(List::copyOf).toList();
        }
    }

    private final SequencedMap<String, BigDecimal> ffmcapById;
    private final BigDecimal cap;
    /** Constituents set to the cap so far; grows each round. */
    private final Set<String> capped = new LinkedHashSet<>();

    private WeightCapping(SequencedMap<String, BigDecimal> ffmcapById, BigDecimal cap) {
        this.ffmcapById = ffmcapById;
        this.cap = cap;
    }

    /**
     * @param ffmcapById FFMCAP of each constituent, all positive
     * @param cap        maximum weight of each constituent
     * @throws IllegalArgumentException if the constituents can't reach 100% even all at the cap
     */
    static Result cap(SequencedMap<String, BigDecimal> ffmcapById, BigDecimal cap) {
        if (ffmcapById.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        // With n constituents at most at the cap each, the weights can only add up to 100% if n × cap ≥ 1.
        if (cap.multiply(BigDecimal.valueOf(ffmcapById.size())).compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException(
                    ffmcapById.size() + " constituents capped at " + cap + " cannot add up to 100%");
        }
        return new WeightCapping(ffmcapById, cap).run();
    }

    /** Weighs the constituents, caps whoever is above the cap, and repeats until nobody is. */
    private Result run() {
        var rounds = new ArrayList<List<String>>();
        while (true) {
            var weights = weightsThisRound();
            var aboveCap = aboveCap(weights);
            if (aboveCap.isEmpty()) {
                var result = finalWeights(weights);
                checkInvariants(result, cap);
                return new Result(result, rounds);
            }
            capped.addAll(aboveCap);
            rounds.add(aboveCap);
        }
    }

    /**
     * The weights of one round. Each capped constituent gets exactly the cap. The weight left over goes to the
     * uncapped constituents, split in proportion to their FFMCAP. In the brief's example, once A is capped at 50%,
     * B and C split the other 50% as 300 : 100, so B gets 37.5% and C 12.5%.
     */
    private Map<String, BigDecimal> weightsThisRound() {
        BigDecimal weightForUncapped = BigDecimal.ONE.subtract(cap.multiply(BigDecimal.valueOf(capped.size())));
        BigDecimal uncappedFfmcap = uncappedFfmcap();

        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        for (var entry : ffmcapById.entrySet()) {
            String id = entry.getKey();
            if (capped.contains(id)) {
                weights.put(id, cap);
            } else {
                // Its share of the uncapped FFMCAP times the weight for the uncapped. Multiplying before dividing
                // leaves a single rounding step.
                weights.put(id, entry.getValue().multiply(weightForUncapped).divide(uncappedFfmcap, PRECISION));
            }
        }
        return weights;
    }

    private BigDecimal uncappedFfmcap() {
        return sum(ffmcapById.entrySet().stream()
                .filter(e -> !capped.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList());
    }

    /**
     * Constituents above the cap that aren't capped yet. Capped ones sit exactly at the cap, so they would never
     * qualify; excluding them says so instead of relying on it.
     */
    private List<String> aboveCap(Map<String, BigDecimal> weights) {
        return weights.entrySet().stream()
                .filter(e -> !capped.contains(e.getKey()) && e.getValue().compareTo(cap) > 0)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * The final weights with the raw weight and capping factor of each constituent. The capping factor scales
     * FFMCAP to the final weight: weight per unit of FFMCAP, divided by the largest such value, so uncapped
     * constituents get exactly 1 and capped ones less (A11).
     */
    private List<CappedWeight> finalWeights(Map<String, BigDecimal> weights) {
        Map<String, BigDecimal> weightPerFfmcap = new LinkedHashMap<>();
        for (var entry : ffmcapById.entrySet()) {
            weightPerFfmcap.put(entry.getKey(), weights.get(entry.getKey()).divide(entry.getValue(), PRECISION));
        }
        BigDecimal largestWeightPerFfmcap =
                weightPerFfmcap.values().stream().reduce(BigDecimal::max).orElseThrow();
        BigDecimal totalFfmcap = sum(ffmcapById.values());

        var result = new ArrayList<CappedWeight>();
        for (var entry : ffmcapById.entrySet()) {
            String id = entry.getKey();
            BigDecimal ffmcap = entry.getValue();
            BigDecimal rawWeight = ffmcap.divide(totalFfmcap, PRECISION);
            BigDecimal cappingFactor = weightPerFfmcap.get(id).divide(largestWeightPerFfmcap, PRECISION);
            result.add(new CappedWeight(id, ffmcap, rawWeight, weights.get(id), cappingFactor, capped.contains(id)));
        }
        return result;
    }

    /** Weights add up to 1 and none exceeds the cap. A failure here is a bug, not bad input. */
    static void checkInvariants(List<CappedWeight> weights, BigDecimal cap) {
        BigDecimal total = sum(weights.stream().map(CappedWeight::weight).toList());
        if (total.subtract(BigDecimal.ONE).abs().compareTo(SUM_TOLERANCE) > 0) {
            throw new IllegalStateException("Weights add up to " + total + ", not 1");
        }
        for (var weight : weights) {
            if (weight.weight().compareTo(cap) > 0) {
                throw new IllegalStateException(
                        weight.securityId() + " weight " + weight.weight() + " exceeds the cap " + cap);
            }
        }
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
