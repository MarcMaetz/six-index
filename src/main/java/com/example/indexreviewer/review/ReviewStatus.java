package com.example.indexreviewer.review;

/** Overall outcome of a review, derived from its data-quality warnings (D10, D16). */
public enum ReviewStatus {
    /** No warnings. */
    COMPLETED,
    /** Warnings, but none can change the composition or the weights. */
    COMPLETED_WITH_WARNINGS,
    /** Missing data affects a current constituent or a security that could rank within the buffer. */
    REQUIRES_ATTENTION
}
