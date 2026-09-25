package com.example.indexreviewer.review;

import java.math.BigDecimal;

/**
 * The criterion securities are ranked by, selected by name in {@code config/indices.yml} (D11). Higher values
 * rank higher. The brief ranks by FFMCAP; the rulebook's selection list (A4) would be another implementation.
 */
public interface RankingStrategy {

    String name();

    BigDecimal rankingValue(EligibleSecurity security);
}
