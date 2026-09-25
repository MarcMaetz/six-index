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
 * Iterative weight capping (rulebook 5.12.4, brief's example): every constituent above the cap is set to the
 * cap, and the remaining weight is shared among the others in proportion to their FFMCAP. That can push another
 * constituent over the cap, so it repeats until none is above it. Calculated at full precision (D13).
 */
public final class WeightCapping {

    static final MathContext PRECISION = MathContext.DECIMAL128;
    static final BigDecimal SUM_TOLERANCE = new BigDecimal("1E-20");

    /**
     * @param weights constituents' weights, in the order given
     * @param rounds  ids capped in each round, for traceability; empty if no constituent exceeded the cap
     */
    public record Result(List<CappedWeight> weights, List<List<String>> rounds) {
        public Result {
            weights = List.copyOf(weights);
            rounds = rounds.stream().map(List::copyOf).toList();
        }
    }

    private WeightCapping() {
    }

    /**
     * @param ffmcapById FFMCAP of each constituent, all positive
     * @param cap        maximum weight; constituents × cap must be at least 1
     */
    public static Result cap(SequencedMap<String, BigDecimal> ffmcapById, BigDecimal cap) {
        if (ffmcapById.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        if (cap.multiply(BigDecimal.valueOf(ffmcapById.size())).compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException(ffmcapById.size() + " constituents capped at " + cap
                    + " cannot add up to 100%");
        }
        BigDecimal total = sum(ffmcapById.values());

        Set<String> capped = new LinkedHashSet<>();
        var rounds = new ArrayList<List<String>>();
        Map<String, BigDecimal> weights;
        while (true) {
            weights = distribute(ffmcapById, capped, cap);
            List<String> overCap = weights.entrySet().stream()
                    .filter(e -> !capped.contains(e.getKey()) && e.getValue().compareTo(cap) > 0)
                    .map(Map.Entry::getKey)
                    .toList();
            if (overCap.isEmpty()) {
                break;
            }
            capped.addAll(overCap);
            rounds.add(overCap);
        }

        // Capping factor ∝ weight / FFMCAP, normalised so the uncapped constituents get 1.
        Map<String, BigDecimal> ratio = new LinkedHashMap<>();
        weights.forEach((id, weight) -> ratio.put(id, weight.divide(ffmcapById.get(id), PRECISION)));
        BigDecimal maxRatio = ratio.values().stream().reduce(BigDecimal::max).orElseThrow();

        var result = new ArrayList<CappedWeight>();
        for (var entry : ffmcapById.entrySet()) {
            String id = entry.getKey();
            result.add(new CappedWeight(id, entry.getValue(), entry.getValue().divide(total, PRECISION),
                    weights.get(id), ratio.get(id).divide(maxRatio, PRECISION), capped.contains(id)));
        }
        checkInvariants(result, cap);
        return new Result(result, rounds);
    }

    /** Capped constituents get the cap; the rest share the remaining weight in proportion to FFMCAP. */
    private static Map<String, BigDecimal> distribute(Map<String, BigDecimal> ffmcapById, Set<String> capped,
                                                      BigDecimal cap) {
        BigDecimal remaining = BigDecimal.ONE.subtract(cap.multiply(BigDecimal.valueOf(capped.size())));
        BigDecimal uncappedTotal = sum(ffmcapById.entrySet().stream()
                .filter(e -> !capped.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList());
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        ffmcapById.forEach((id, ffmcap) -> weights.put(id, capped.contains(id)
                ? cap
                : ffmcap.multiply(remaining).divide(uncappedTotal, PRECISION)));
        return weights;
    }

    /** D13: weights add up to 1 and none exceeds the cap. A failure here is a bug, not bad input. */
    private static void checkInvariants(List<CappedWeight> weights, BigDecimal cap) {
        BigDecimal sum = sum(weights.stream().map(CappedWeight::weight).toList());
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(SUM_TOLERANCE) > 0) {
            throw new IllegalStateException("Weights add up to " + sum + ", not 1");
        }
        for (var weight : weights) {
            if (weight.weight().compareTo(cap) > 0) {
                throw new IllegalStateException(weight.securityId() + " weight " + weight.weight()
                        + " exceeds the cap " + cap);
            }
        }
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
