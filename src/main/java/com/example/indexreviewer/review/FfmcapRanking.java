package com.example.indexreviewer.review;

import java.math.BigDecimal;

/** Ranks by free float market capitalization, as the brief specifies. */
public final class FfmcapRanking implements RankingStrategy {

    public static final String NAME = "FFMCAP";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public BigDecimal rankingValue(EligibleSecurity security) {
        return security.ffmcap();
    }
}
