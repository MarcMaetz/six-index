package com.example.indexreviewer.report;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.review.ReviewResult;
import com.example.indexreviewer.review.status.StatusAssessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;

/**
 * Turns a {@link ReviewResult} into a {@link ReviewReport}, rounding values for display only: weights to 6
 * decimals in percent, capping factors to 10, FFMCAP to 2.
 */
public final class ReportBuilder {

    private static final int WEIGHT_DECIMALS = 6;
    private static final int CAPPING_FACTOR_DECIMALS = 10;
    private static final int FFMCAP_DECIMALS = 2;

    private final Clock clock;
    private final ReviewReport.Build build;

    public ReportBuilder(Clock clock, ReviewReport.Build build) {
        this.clock = clock;
        this.build = build;
    }

    public ReviewReport build(ReviewResult result) {
        var index = result.index();
        var period = result.period();
        var status = StatusAssessment.assess(result);

        var joiners = result.joiners().stream()
                .map(c -> new ReviewReport.Joiner(c.securityId(), c.rank(), c.decision()))
                .toList();

        return new ReviewReport(index.name(), period.id(), period.cutOffDate(), period.reviewDate(),
                clock.instant(), build, status.status(), status.reasons(), parameters(index), constituents(result),
                joiners, result.leavers(), result.excluded(), ranking(result), result.cappingRounds(), result.input().files(),
                result.warnings());
    }

    private List<ReviewReport.Constituent> constituents(ReviewResult result) {
        return result.constituents().stream()
                .map(c -> new ReviewReport.Constituent(c.rank(), c.securityId(), c.decision(), c.joiner(),
                        ffmcap(c.weight().ffmcap()), percent(c.weight().rawWeight()), percent(c.weight().weight()),
                        round(c.weight().cappingFactor(), CAPPING_FACTOR_DECIMALS), c.weight().capped()))
                .toList();
    }

    private List<ReviewReport.RankingEntry> ranking(ReviewResult result) {
        return result.selection().stream()
                .map(o -> {
                    var eligible = o.ranked().eligible();
                    return new ReviewReport.RankingEntry(o.rank(), o.securityId(), eligible.price(), eligible.shares(),
                            eligible.freeFloat(), ffmcap(eligible.ffmcap()), o.incumbent(), o.decision());
                })
                .toList();
    }

    private static ReviewReport.Parameters parameters(IndexDefinition index) {
        return new ReviewReport.Parameters(index.methodology(), index.universe(),
                index.constituentCount(), index.directSelectionRank(), index.bufferEndRank(),
                index.weightCap().movePointRight(2).stripTrailingZeros(), index.rankingStrategy());
    }

    private BigDecimal percent(BigDecimal fraction) {
        return round(fraction.movePointRight(2), WEIGHT_DECIMALS);
    }

    private BigDecimal ffmcap(BigDecimal ffmcap) {
        return round(ffmcap, FFMCAP_DECIMALS);
    }

    private static BigDecimal round(BigDecimal value, int decimals) {
        return value.setScale(decimals, RoundingMode.HALF_EVEN);
    }
}
