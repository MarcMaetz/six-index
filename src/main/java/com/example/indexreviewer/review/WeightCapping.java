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
 * Iterative weight capping (rulebook 5.12.4, brief's example): every constituent above its cap is set to its cap,
 * and the remaining weight is shared among the others in proportion to their FFMCAP. That can push another
 * constituent over its cap, so it repeats until none is above it. The {@link CappingRule} decides each
 * constituent's cap. Calculated at full precision.
 */
final class WeightCapping {

    static final MathContext PRECISION = MathContext.DECIMAL128;
    static final BigDecimal SUM_TOLERANCE = new BigDecimal("1E-20");

    /**
     * @param weights constituents' weights, in the order given
     * @param rounds  ids capped in each round, for traceability; empty if no constituent exceeded its cap
     */
    record Result(List<CappedWeight> weights, List<List<String>> rounds) {
        Result {
            weights = List.copyOf(weights);
            rounds = rounds.stream().map(List::copyOf).toList();
        }
    }

    private WeightCapping() {
    }

    /**
     * @param ffmcapById FFMCAP of each constituent, all positive
     * @param rule       decides each constituent's cap; the caps must add up to at least 1
     */
    static Result cap(SequencedMap<String, BigDecimal> ffmcapById, CappingRule rule) {
        if (ffmcapById.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        Map<String, BigDecimal> caps = caps(ffmcapById, rule);

        Set<String> capped = new LinkedHashSet<>();
        var rounds = new ArrayList<List<String>>();
        var weights = distribute(ffmcapById, capped, caps);
        var overCap = overCap(weights, capped, caps);
        while (!overCap.isEmpty()) {
            capped.addAll(overCap);
            rounds.add(overCap);
            weights = distribute(ffmcapById, capped, caps);
            overCap = overCap(weights, capped, caps);
        }

        var result = cappedWeights(ffmcapById, weights, capped);
        checkInvariants(result, caps);
        return new Result(result, rounds);
    }

    /** Each constituent's cap; fails if the caps cannot add up to 100%. */
    private static Map<String, BigDecimal> caps(SequencedMap<String, BigDecimal> ffmcapById, CappingRule rule) {
        Map<String, BigDecimal> caps = rule.caps(ffmcapById);
        if (sum(caps.values()).compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException(ffmcapById.size() + " constituents with caps adding up to "
                    + sum(caps.values()) + " (" + rule + ") cannot add up to 100%");
        }
        return caps;
    }

    /** Constituents not yet capped whose weight exceeds their cap. */
    private static List<String> overCap(Map<String, BigDecimal> weights, Set<String> capped,
                                        Map<String, BigDecimal> caps) {
        return weights.entrySet().stream()
                .filter(e -> !capped.contains(e.getKey()) && e.getValue().compareTo(caps.get(e.getKey())) > 0)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Capped constituents get their cap; the rest share the remaining weight in proportion to FFMCAP. */
    private static Map<String, BigDecimal> distribute(Map<String, BigDecimal> ffmcapById, Set<String> capped,
                                                      Map<String, BigDecimal> caps) {
        BigDecimal remaining = BigDecimal.ONE.subtract(sum(capped.stream().map(caps::get).toList()));
        BigDecimal uncappedTotal = sum(ffmcapById.entrySet().stream()
                .filter(e -> !capped.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList());
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        ffmcapById.forEach((id, ffmcap) -> weights.put(id, capped.contains(id)
                ? caps.get(id)
                : ffmcap.multiply(remaining).divide(uncappedTotal, PRECISION)));
        return weights;
    }

    /** Capping factor ∝ weight / FFMCAP, normalised so the uncapped constituents get 1. */
    private static List<CappedWeight> cappedWeights(SequencedMap<String, BigDecimal> ffmcapById,
                                                    Map<String, BigDecimal> weights, Set<String> capped) {
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

    /** Weights add up to 1 and none exceeds its cap. A failure here is a bug, not bad input. */
    static void checkInvariants(List<CappedWeight> weights, Map<String, BigDecimal> caps) {
        BigDecimal sum = sum(weights.stream().map(CappedWeight::weight).toList());
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(SUM_TOLERANCE) > 0) {
            throw new IllegalStateException("Weights add up to " + sum + ", not 1");
        }
        for (var weight : weights) {
            BigDecimal cap = caps.get(weight.securityId());
            if (weight.weight().compareTo(cap) > 0) {
                throw new IllegalStateException(weight.securityId() + " weight " + weight.weight()
                        + " exceeds its cap " + cap);
            }
        }
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
