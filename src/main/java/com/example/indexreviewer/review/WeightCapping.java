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
 * Iterative weight capping (rulebook 5.12.4, brief's example): every constituent above the cap is set to the cap,
 * and the remaining weight is shared among the others in proportion to their FFMCAP. That can push another
 * constituent over the cap, so it repeats until none is above it. One cap applies to all constituents.
 * Calculated at full precision.
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
     * @param cap        maximum weight of each constituent; times the number of constituents it must be at least 1
     */
    static Result cap(SequencedMap<String, BigDecimal> ffmcapById, BigDecimal cap) {
        if (ffmcapById.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        if (cap.multiply(BigDecimal.valueOf(ffmcapById.size())).compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException(
                    ffmcapById.size() + " constituents capped at " + cap + " cannot add up to 100%");
        }
        return new WeightCapping(ffmcapById, cap).cap();
    }

    private Result cap() {
        var rounds = new ArrayList<List<String>>();
        var weights = distribute();
        var overCap = overCap(weights);
        while (!overCap.isEmpty()) {
            capped.addAll(overCap);
            rounds.add(overCap);
            weights = distribute();
            overCap = overCap(weights);
        }

        var result = cappedWeights(weights);
        checkInvariants(result, cap);
        return new Result(result, rounds);
    }

    /** Constituents not yet capped whose weight exceeds the cap. */
    private List<String> overCap(Map<String, BigDecimal> weights) {
        return weights.entrySet().stream()
                .filter(e -> !capped.contains(e.getKey()) && e.getValue().compareTo(cap) > 0)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Capped constituents get the cap; the rest share the remaining weight in proportion to FFMCAP. */
    private Map<String, BigDecimal> distribute() {
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

    /** Capping factor ∝ weight / FFMCAP, normalised so the uncapped constituents get 1. */
    private List<CappedWeight> cappedWeights(Map<String, BigDecimal> weights) {
        BigDecimal total = sum(ffmcapById.values());
        Map<String, BigDecimal> ratio = new LinkedHashMap<>();
        weights.forEach((id, weight) -> ratio.put(id, weight.divide(ffmcapById.get(id), PRECISION)));
        BigDecimal maxRatio = ratio.values().stream().reduce(BigDecimal::max).orElseThrow();

        var result = new ArrayList<CappedWeight>();
        for (var entry : ffmcapById.entrySet()) {
            String id = entry.getKey();
            result.add(new CappedWeight(id, entry.getValue(), entry.getValue().divide(total, PRECISION),
                    weights.get(id), ratio.get(id).divide(maxRatio, PRECISION), capped.contains(id)));
        }
        return result;
    }

    /** Weights add up to 1 and none exceeds the cap. A failure here is a bug, not bad input. */
    static void checkInvariants(List<CappedWeight> weights, BigDecimal cap) {
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
