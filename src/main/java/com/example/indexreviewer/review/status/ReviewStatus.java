package com.example.indexreviewer.review.status;

import java.util.List;

/** Overall outcome of a review, derived from its data-quality warnings. */
public enum ReviewStatus {
    /** No warnings. */
    COMPLETED,
    /** Warnings, but none can change the composition or the weights. */
    COMPLETED_WITH_WARNINGS,
    /** Missing data affects a current constituent or a security that could rank within the buffer. */
    REQUIRES_ATTENTION;

    /** {@code REQUIRES_ATTENTION} if any reason needs attention, otherwise whether there are reasons at all. */
    static ReviewStatus of(List<StatusReason> reasons) {
        if (reasons.stream().anyMatch(r -> r.relevance().needsAttention())) {
            return REQUIRES_ATTENTION;
        }
        return reasons.isEmpty() ? COMPLETED : COMPLETED_WITH_WARNINGS;
    }
}
