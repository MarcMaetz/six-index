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
 * last is estimated from whatever data the security has; if even that is impossible, it counts as relevant.
 */
final class StatusAssessment {

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
            return estimatedRank(id)
                    .map(rank -> rank <= bufferEnd
                            ? Optional.of("not ranked, estimated rank " + rank + " is within buffer end " + bufferEnd)
                            : Optional.<String>empty())
                    .orElse(Optional.of("not ranked, and too little data to estimate its rank"));
        }

        String irrelevance(String id) {
            var rankedSecurity = rankedById.get(id);
            if (rankedSecurity != null) {
                return "ranked " + rankedSecurity.rank() + ", below buffer end " + result.index().bufferEndRank();
            }
            return "not ranked, estimated rank " + estimatedRank(id).orElseThrow()
                    + " is below buffer end " + result.index().bufferEndRank();
        }

        /**
         * Rank an unranked security would get with the values it has on either date: price preferably from the
         * cut-off date, shares and free float preferably from the review date.
         */
        private Optional<Integer> estimatedRank(String id) {
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
            BigDecimal value = RankingStrategies.byName(result.index().rankingStrategy())
                    .rankingValue(EligibleSecurity.of(id, price, shares, freeFloat));
            return Optional.of(1 + (int) ranked.stream().filter(r -> r.rankingValue().compareTo(value) > 0).count());
        }

        private static <T> T first(Optional<T> preferred, Optional<T> fallback) {
            return preferred.or(() -> fallback).orElse(null);
        }
    }
}
