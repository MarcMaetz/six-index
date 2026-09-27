package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.RankingStrategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Ranks eligible securities by their ranking value under a strategy, highest first; ties are broken by id (A8). */
final class Ranking {

    private Ranking() {
    }

    static List<RankedSecurity> rank(List<EligibleSecurity> eligible, RankingStrategy strategy,
                                     Set<String> currentComposition) {
        record Scored(EligibleSecurity security, BigDecimal value) {
        }
        List<Scored> sorted = eligible.stream()
                .map(s -> new Scored(s, s.rankingValue(strategy)))
                .sorted(Comparator.comparing(Scored::value).reversed()
                        .thenComparing(scored -> scored.security().securityId()))
                .toList();
        var ranked = new ArrayList<RankedSecurity>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            var scored = sorted.get(i);
            ranked.add(new RankedSecurity(i + 1, scored.security(), scored.value(),
                    currentComposition.contains(scored.security().securityId())));
        }
        return ranked;
    }
}
