package com.example.indexreviewer.review;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The ranking strategies the application knows, looked up by the name used in the configuration. */
public final class RankingStrategies {

    private static final Map<String, RankingStrategy> BY_NAME = Stream.<RankingStrategy>of(new FfmcapRanking())
            .collect(Collectors.toUnmodifiableMap(RankingStrategy::name, Function.identity()));

    private RankingStrategies() {
    }

    public static RankingStrategy byName(String name) {
        var strategy = BY_NAME.get(name);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unknown ranking strategy " + name + ", known: " + BY_NAME.keySet());
        }
        return strategy;
    }
}
