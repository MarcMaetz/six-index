package com.example.indexreviewer.report;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;

/**
 * Why the review has its status (D22): one entry per warning and affected security, or for an incomplete index.
 *
 * @param securityId  affected security, or {@code null} if the reason names none
 * @param relevance   whether and why it can change the result; decides the status
 * @param explanation the same, as a sentence for people
 * @param warning     the warning this reason comes from, or {@code null} for {@link Relevance#INDEX_INCOMPLETE}
 */
public record StatusReason(String securityId, Relevance relevance, String explanation, WarningRef warning) {

    public enum Relevance {
        /** Missing data on a security in the current composition. */
        CURRENT_CONSTITUENT(true),
        /** Missing data on a security ranked within the buffer end. */
        RANKED_WITHIN_BUFFER(true),
        /** Unranked security whose estimate is not clearly below the buffer end (A13). */
        ESTIMATED_NEAR_BUFFER(true),
        /** Unranked security without enough data to estimate its ranking value. */
        NOT_ESTIMABLE(true),
        /** Unranked security while fewer securities are ranked than the buffer end: it could take a place. */
        BUFFER_NOT_FULL(true),
        /** A warning with missing data that names no security. */
        SECURITY_UNKNOWN(true),
        /** Fewer constituents could be selected than the index needs (A12). */
        INDEX_INCOMPLETE(true),
        /** The warning lost no data, e.g. an identical duplicate row. */
        NO_DATA_LOST(false),
        /** Missing data on a security ranked below the buffer end. */
        RANKED_BELOW_BUFFER(false),
        /** Unranked security whose estimate is well below the buffer end (A13). */
        ESTIMATED_FAR_BELOW_BUFFER(false);

        private final boolean needsAttention;

        Relevance(boolean needsAttention) {
            this.needsAttention = needsAttention;
        }

        public boolean needsAttention() {
            return needsAttention;
        }
    }

    /** The warning a reason refers to, without its list of ids (the reason names the one that matters). */
    public record WarningRef(String source, Integer line, Impact impact, String message) {

        static WarningRef of(DataQualityWarning warning) {
            return new WarningRef(warning.source(), warning.line(), warning.impact(), warning.message());
        }
    }
}
