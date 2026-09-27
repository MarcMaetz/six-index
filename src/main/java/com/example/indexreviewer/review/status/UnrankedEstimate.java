package com.example.indexreviewer.review.status;

import com.example.indexreviewer.domain.SecurityData;
import com.example.indexreviewer.review.EligibleSecurity;
import com.example.indexreviewer.review.RankedSecurity;
import com.example.indexreviewer.review.ReviewResult;
import com.example.indexreviewer.review.status.StatusReason.Relevance;
import com.example.indexreviewer.review.status.StatusReason.WarningRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * A13: judges missing data on a security that could not be ranked. Its ranking value is estimated from
 * whatever data it has on either date, and it is harmless only if the estimate is well below the buffer: below
 * {@link #HARMLESS_SHARE_OF_BUFFER_END} of the ranking value at the buffer end rank. The margin covers the data
 * the estimate borrows from the other date. It only judges; it never selects or weights.
 */
final class UnrankedEstimate {

    /** A13: share of the buffer-end ranking value below which an unranked security's estimate is harmless. */
    static final BigDecimal HARMLESS_SHARE_OF_BUFFER_END = new BigDecimal("0.5");

    private final ReviewResult result;
    private final int bufferEnd;
    /** Ranking value at the buffer end rank; empty if fewer securities are ranked than the buffer end. */
    private final Optional<BigDecimal> bufferEndValue;

    UnrankedEstimate(ReviewResult result, List<RankedSecurity> ranked) {
        this.result = result;
        this.bufferEnd = result.index().bufferEndRank();
        this.bufferEndValue = ranked.size() < bufferEnd
                ? Optional.empty()
                : Optional.of(ranked.get(bufferEnd - 1).rankingValue());
    }

    StatusReason judge(String id, WarningRef warning) {
        var estimate = estimatedValue(id);
        if (estimate.isEmpty()) {
            return new StatusReason(
                    id,
                    Relevance.NOT_ESTIMABLE,
                    "Not ranked, and too little data to estimate its ranking value",
                    warning);
        }
        if (bufferEndValue.isEmpty()) {
            return new StatusReason(
                    id,
                    Relevance.BUFFER_NOT_FULL,
                    ("Not ranked, and fewer than %d securities are " + "ranked, so it could reach the buffer")
                            .formatted(bufferEnd),
                    warning);
        }
        var value = bufferEndValue.get();
        boolean farBelow = estimate.get().compareTo(value.multiply(HARMLESS_SHARE_OF_BUFFER_END)) < 0;
        String explanation = "Not ranked, estimated %s %s is %s half the value at buffer end rank %d (%s)"
                .formatted(
                        result.index().rankingStrategy(),
                        plain(estimate.get()),
                        farBelow ? "below" : "not below",
                        bufferEnd,
                        plain(value));
        return new StatusReason(
                id,
                farBelow ? Relevance.ESTIMATED_FAR_BELOW_BUFFER : Relevance.ESTIMATED_NEAR_BUFFER,
                explanation,
                warning);
    }

    private static String plain(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_EVEN).toPlainString();
    }

    /**
     * Ranking value the security would get with the values it has on either date: price preferably from the
     * cut-off date, shares and free float preferably from the review date.
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
        return Optional.of(EligibleSecurity.of(id, price.get(), shares.get(), freeFloat.get())
                .rankingValue(result.index().rankingStrategy()));
    }
}
