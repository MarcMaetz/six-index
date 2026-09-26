package com.example.indexreviewer.review;

import java.math.BigDecimal;

/**
 * The criterion securities are ranked by, selected by name in {@code config/indices.yml} (D11). Higher values
 * rank higher. A strategy sees one security's price, shares and free float, so it can rank by any criterion
 * computed from those. The rulebook's selection list (A4) needs turnover and 12 months of history, which the
 * input doesn't have: it takes new input data and a wider signature, not just another implementation (D26).
 */
public interface RankingStrategy {

    String name();

    BigDecimal rankingValue(EligibleSecurity security);
}
