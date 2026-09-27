package com.example.indexreviewer.domain;

/**
 * The criterion securities are ranked by, chosen by name in {@code config/indices.yml}; an unknown name stops
 * startup. Higher values rank higher. {@code EligibleSecurity.rankingValue} computes each one, and its
 * {@code switch} has no default, so a new constant doesn't compile until it says how to rank.
 * <p>
 * A criterion computed from price, shares and free float is one constant and one {@code case}. The rulebook's
 * selection list (A4) needs turnover and 12 months of history, which the input doesn't have: it takes new input
 * data, not just another constant.
 */
public enum RankingStrategy {

    /** Free float market capitalization, as the brief specifies. */
    FFMCAP
}
