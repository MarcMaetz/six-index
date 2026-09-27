package com.example.indexreviewer.review;

/** A security in the new composition: its rank, why it was selected, whether it joins, and its weight. */
public record Constituent(RankedSecurity ranked, SelectionDecision decision, boolean joiner, CappedWeight weight) {

    public String securityId() {
        return ranked.securityId();
    }

    public int rank() {
        return ranked.rank();
    }
}
