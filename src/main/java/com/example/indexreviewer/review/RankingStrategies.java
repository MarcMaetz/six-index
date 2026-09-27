package com.example.indexreviewer.review;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The ranking strategies the application knows, looked up by the name used in the configuration. A static
 * registry, not Spring beans: {@code IndexCatalog} checks the names at startup, {@code ReviewEngine} looks
 * up the strategy once per review. Strategies become beans wired in {@code config} once one needs outside
 * dependencies.
 */
public final class RankingStrategies {

    /** Ranks by free float market capitalization, as the brief specifies. */
    static final RankingStrategy FFMCAP = new ByValue("FFMCAP", EligibleSecurity::ffmcap);

    private static final Map<String, RankingStrategy> BY_NAME = Stream.of(FFMCAP)
            .collect(Collectors.toUnmodifiableMap(RankingStrategy::name, Function.identity()));

    /** A strategy that reads one value of the security. */
    private record ByValue(String name, Function<EligibleSecurity, BigDecimal> value) implements RankingStrategy {

        @Override
        public BigDecimal rankingValue(EligibleSecurity security) {
            return value.apply(security);
        }
    }

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
