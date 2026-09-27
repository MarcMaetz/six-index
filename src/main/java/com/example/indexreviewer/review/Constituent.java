package com.example.indexreviewer.review;

/** A security in the new composition: its selection outcome and its weight. */
public record Constituent(Selection.Outcome selection, CappedWeight weight) {

    public String securityId() {
        return selection.securityId();
    }

    public int rank() {
        return selection.rank();
    }

    public SelectionDecision decision() {
        return selection.decision();
    }

    /** Selected without being a current constituent. */
    public boolean joiner() {
        return !selection.incumbent();
    }
}
