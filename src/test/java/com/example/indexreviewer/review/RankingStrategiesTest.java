package com.example.indexreviewer.review;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RankingStrategiesTest {

    @Test
    void findsStrategyByConfiguredName() {
        assertThat(RankingStrategies.byName("FFMCAP").name()).isEqualTo("FFMCAP");
    }

    @Test
    void rejectsUnknownName() {
        assertThatIllegalArgumentException().isThrownBy(() -> RankingStrategies.byName("TURNOVER"))
                .withMessage("Unknown ranking strategy TURNOVER, known: [FFMCAP]");
    }
}
