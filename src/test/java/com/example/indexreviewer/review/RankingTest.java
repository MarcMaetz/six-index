package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.RankingStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RankingTest {

    @Test
    void ranksHighestValueFirstAndBreaksTiesById() {
        // B and A tie at 100 and are given in that order: A ranks first by id (A8).
        var ranked = Ranking.rank(List.of(eligible("C", 50), eligible("B", 100), eligible("A", 100)),
                RankingStrategy.FFMCAP, Set.of("B"));

        assertThat(ranked).extracting(RankedSecurity::rank, RankedSecurity::securityId, RankedSecurity::incumbent)
                .containsExactly(tuple(1, "A", false), tuple(2, "B", true), tuple(3, "C", false));
    }

    private static EligibleSecurity eligible(String id, long shares) {
        return EligibleSecurity.of(id, BigDecimal.ONE, shares, BigDecimal.ONE);
    }
}
