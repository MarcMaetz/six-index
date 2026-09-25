package com.example.indexreviewer.review;

/**
 * A current constituent that is not in the new composition.
 *
 * @param rank   its rank, or {@code null} if it could not be ranked
 * @param reason why it leaves
 */
public record Leaver(String securityId, Integer rank, LeaveReason reason, String detail) {

    public enum LeaveReason {
        /** No longer in the index universe on the review date. */
        NOT_IN_UNIVERSE,
        /** In the universe, but data needed for ranking is missing (A2). */
        NOT_ELIGIBLE,
        /** Ranked in the buffer, but the slots went to higher-ranked incumbents. */
        BUFFER_FULL,
        /** Ranked below the buffer. */
        BELOW_BUFFER
    }
}
