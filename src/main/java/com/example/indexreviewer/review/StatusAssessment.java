package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;
import com.example.indexreviewer.domain.SecurityData;
import com.example.indexreviewer.review.StatusReason.Relevance;
import com.example.indexreviewer.review.StatusReason.WarningRef;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derives the review status from the warnings. Every warning yields a {@link StatusReason} per
 * affected security, with a {@link Relevance}: missing data matters when it names no security, or a security
 * that could change the result: a current constituent, a security ranked within the buffer end, or an unranked
 * security that could rank there. The last is estimated from whatever data the security has and must be well
 * below the buffer end to count as harmless (A13). An index with fewer constituents than needed always needs
 * attention (A12). The status is {@code REQUIRES_ATTENTION} if any reason needs attention.
 * <p>
 * Part of the review, not the report: it judges the result using the index's buffer and ranking strategy.
 * Read it through {@link ReviewResult#assessment()}.
 */
public final class StatusAssessment {

    /** A13: share of the buffer-end ranking value below which an unranked security's estimate is harmless. */
    static final BigDecimal HARMLESS_SHARE_OF_BUFFER_END = new BigDecimal("0.5");

    /** The review status and every reason behind it, harmless ones included. */
    public record Result(ReviewStatus status, List<StatusReason> reasons) {
    }

    private StatusAssessment() {
    }

    static Result assess(ReviewResult result) {
        var context = new Context(result);
        var reasons = new ArrayList<StatusReason>();
        for (var warning : result.warnings()) {
            reasons.addAll(reasons(warning, context));
        }
        int selected = result.constituents().size();
        int needed = result.index().constituentCount();
        if (selected < needed) {
            reasons.add(new StatusReason(null, Relevance.INDEX_INCOMPLETE,
                    "Only %d securities could be selected, %d needed".formatted(selected, needed), null));
        }
        return new Result(status(reasons), reasons);
    }

    /** One reason per affected security, or one without a security if the warning names none. */
    private static List<StatusReason> reasons(DataQualityWarning warning, Context context) {
        var ref = WarningRef.of(warning);
        if (warning.impact() == Impact.NONE) {
            return List.of(new StatusReason(null, Relevance.NO_DATA_LOST, "No data lost", ref));
        }
        if (warning.securityIds().isEmpty()) {
            return List.of(new StatusReason(null, Relevance.SECURITY_UNKNOWN,
                    "Data is missing, but the affected security is unknown", ref));
        }
        return warning.securityIds().stream().map(id -> context.assess(id, ref)).toList();
    }

    private static ReviewStatus status(List<StatusReason> reasons) {
        if (reasons.stream().anyMatch(r -> r.relevance().needsAttention())) {
            return ReviewStatus.REQUIRES_ATTENTION;
        }
        return reasons.isEmpty() ? ReviewStatus.COMPLETED : ReviewStatus.COMPLETED_WITH_WARNINGS;
    }

    private static final class Context {
        private final ReviewResult result;
        private final Map<String, RankedSecurity> rankedById;
        private final List<RankedSecurity> ranked;

        Context(ReviewResult result) {
            this.result = result;
            this.ranked = result.selection().stream().map(Selection.Outcome::ranked).toList();
            this.rankedById = ranked.stream()
                    .collect(Collectors.toMap(RankedSecurity::securityId, Function.identity()));
        }

        /** How missing data on one security relates to the result. */
        StatusReason assess(String id, WarningRef warning) {
            int bufferEnd = result.index().bufferEndRank();
            if (result.input().currentComposition().contains(id)) {
                return new StatusReason(id, Relevance.CURRENT_CONSTITUENT, "Current constituent", warning);
            }
            var rankedSecurity = rankedById.get(id);
            if (rankedSecurity != null) {
                int rank = rankedSecurity.rank();
                return rank <= bufferEnd
                        ? new StatusReason(id, Relevance.RANKED_WITHIN_BUFFER,
                                "Ranked %d, within buffer end %d".formatted(rank, bufferEnd), warning)
                        : new StatusReason(id, Relevance.RANKED_BELOW_BUFFER,
                                "Ranked %d, below buffer end %d".formatted(rank, bufferEnd), warning);
            }
            return unranked(id, warning);
        }

        /** An unranked security, judged by its estimated ranking value against the buffer end (A13). */
        private StatusReason unranked(String id, WarningRef warning) {
            int bufferEnd = result.index().bufferEndRank();
            var estimate = estimatedValue(id);
            if (estimate.isEmpty()) {
                return new StatusReason(id, Relevance.NOT_ESTIMABLE,
                        "Not ranked, and too little data to estimate its ranking value", warning);
            }
            var threshold = harmlessBelow();
            if (threshold.isEmpty()) {
                return new StatusReason(id, Relevance.BUFFER_NOT_FULL, ("Not ranked, and fewer than %d securities are "
                        + "ranked, so it could reach the buffer").formatted(bufferEnd), warning);
            }
            return estimate.get().compareTo(threshold.get()) < 0
                    ? new StatusReason(id, Relevance.ESTIMATED_FAR_BELOW_BUFFER,
                            "Not ranked, " + comparedToBufferEnd(estimate.get(), "is below"), warning)
                    : new StatusReason(id, Relevance.ESTIMATED_NEAR_BUFFER,
                            "Not ranked, " + comparedToBufferEnd(estimate.get(), "is not below"), warning);
        }

        /**
         * A13: an unranked security is harmless only if its estimate is well below the buffer: below
         * {@link #HARMLESS_SHARE_OF_BUFFER_END} of the ranking value at the buffer end rank. The margin covers the
         * data the estimate borrows from the other date. Empty if fewer securities are ranked than the buffer end.
         */
        private Optional<BigDecimal> harmlessBelow() {
            int bufferEnd = result.index().bufferEndRank();
            return ranked.size() < bufferEnd
                    ? Optional.empty()
                    : Optional.of(ranked.get(bufferEnd - 1).rankingValue().multiply(HARMLESS_SHARE_OF_BUFFER_END));
        }

        private String comparedToBufferEnd(BigDecimal estimate, String comparison) {
            int bufferEnd = result.index().bufferEndRank();
            return "estimated %s %s %s half the value at buffer end rank %d (%s)".formatted(
                    result.index().rankingStrategy(), plain(estimate), comparison, bufferEnd,
                    plain(ranked.get(bufferEnd - 1).rankingValue()));
        }

        private static String plain(BigDecimal value) {
            return value.setScale(0, RoundingMode.HALF_EVEN).toPlainString();
        }

        /**
         * Ranking value an unranked security would get with the values it has on either date: price preferably
         * from the cut-off date, shares and free float preferably from the review date.
         */
        private Optional<BigDecimal> estimatedValue(String id) {
            var input = result.input();
            var period = result.period();
            var cutOff = input.securityData(id, period.cutOffDate());
            var review = input.securityData(id, period.reviewDate());
            var price = cutOff.map(SecurityData::price).or(() -> review.map(SecurityData::price));
            var shares = review.map(SecurityData::shares).or(() -> cutOff.map(SecurityData::shares));
            var freeFloat = review.map(SecurityData::freeFloat).or(() -> cutOff.map(SecurityData::freeFloat));
            if (price.isEmpty() || shares.isEmpty() || freeFloat.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result.rankingStrategy()
                    .rankingValue(EligibleSecurity.of(id, price.get(), shares.get(), freeFloat.get())));
        }
    }
}
