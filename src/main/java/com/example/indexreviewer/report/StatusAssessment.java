package com.example.indexreviewer.report;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;
import com.example.indexreviewer.domain.SecurityData;
import com.example.indexreviewer.review.EligibleSecurity;
import com.example.indexreviewer.review.RankedSecurity;
import com.example.indexreviewer.review.RankingStrategies;
import com.example.indexreviewer.review.ReviewResult;
import com.example.indexreviewer.review.Selection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derives the review status from the warnings (D10, D16). A warning needs attention when it reports missing
 * data ({@link Impact#MISSING_DATA}) and names no security, or names a security that matters: a current
 * constituent, a security ranked within the buffer end, or an unranked security that could rank there. The
 * last is estimated from whatever data the security has and must be well below the buffer end to count as
 * harmless (A13); if no estimate is possible, it counts as relevant.
 */
final class StatusAssessment {

    /** A13: share of the buffer-end ranking value below which an unranked security's estimate is harmless. */
    static final BigDecimal HARMLESS_SHARE_OF_BUFFER_END = new BigDecimal("0.5");

    record Result(ReviewStatus status, List<String> reasons) {
    }

    private StatusAssessment() {
    }

    static Result assess(ReviewResult result) {
        var context = new Context(result);
        var attention = new ArrayList<String>();
        var harmless = new ArrayList<String>();
        for (var warning : result.warnings()) {
            if (warning.impact() == Impact.NONE) {
                harmless.add(warning + " (no data lost)");
            } else if (warning.securityIds().isEmpty()) {
                attention.add(warning + " (affected security unknown)");
            } else {
                for (String id : warning.securityIds()) {
                    context.relevance(id).ifPresentOrElse(
                            why -> attention.add(warning + " (" + id + ": " + why + ")"),
                            () -> harmless.add(warning + " (" + id + ": " + context.irrelevance(id) + ")"));
                }
            }
        }
        if (!attention.isEmpty()) {
            return new Result(ReviewStatus.REQUIRES_ATTENTION, attention);
        }
        return harmless.isEmpty()
                ? new Result(ReviewStatus.COMPLETED, List.of())
                : new Result(ReviewStatus.COMPLETED_WITH_WARNINGS, harmless);
    }

    private static final class Context {
        private final ReviewResult result;
        private final Map<String, RankedSecurity> rankedById;
        private final List<RankedSecurity> ranked;

        Context(ReviewResult result) {
            this.result = result;
            this.ranked = result.selection().stream().map(Selection.Outcome::security).toList();
            this.rankedById = ranked.stream()
                    .collect(Collectors.toMap(RankedSecurity::securityId, Function.identity()));
        }

        /** Why the security matters for the result, or empty if it can't change the result. */
        Optional<String> relevance(String id) {
            int bufferEnd = result.index().bufferEndRank();
            if (result.input().currentComposition().contains(id)) {
                return Optional.of("current constituent");
            }
            var rankedSecurity = rankedById.get(id);
            if (rankedSecurity != null) {
                return rankedSecurity.rank() <= bufferEnd
                        ? Optional.of("ranked " + rankedSecurity.rank() + ", within buffer end " + bufferEnd)
                        : Optional.empty();
            }
            var estimate = estimatedValue(id);
            if (estimate.isEmpty()) {
                return Optional.of("not ranked, and too little data to estimate its ranking value");
            }
            var threshold = harmlessBelow();
            if (threshold.isEmpty()) {
                return Optional.of("not ranked, and fewer than " + bufferEnd
                        + " securities are ranked, so it could reach the buffer");
            }
            return estimate.get().compareTo(threshold.get()) < 0
                    ? Optional.empty()
                    : Optional.of("not ranked, " + comparedToBufferEnd(estimate.get(), "is not below"));
        }

        String irrelevance(String id) {
            var rankedSecurity = rankedById.get(id);
            if (rankedSecurity != null) {
                return "ranked " + rankedSecurity.rank() + ", below buffer end " + result.index().bufferEndRank();
            }
            return "not ranked, " + comparedToBufferEnd(estimatedValue(id).orElseThrow(), "is below");
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
            BigDecimal price = first(cutOff.map(SecurityData::price), review.map(SecurityData::price));
            Long shares = first(review.map(SecurityData::shares), cutOff.map(SecurityData::shares));
            BigDecimal freeFloat = first(review.map(SecurityData::freeFloat), cutOff.map(SecurityData::freeFloat));
            if (price == null || shares == null || freeFloat == null) {
                return Optional.empty();
            }
            return Optional.of(RankingStrategies.byName(result.index().rankingStrategy())
                    .rankingValue(EligibleSecurity.of(id, price, shares, freeFloat)));
        }

        private static <T> T first(Optional<T> preferred, Optional<T> fallback) {
            return preferred.or(() -> fallback).orElse(null);
        }
    }
}
