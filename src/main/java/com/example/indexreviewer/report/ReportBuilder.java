package com.example.indexreviewer.report;

import com.example.indexreviewer.review.ReviewResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;

/** Turns a {@link ReviewResult} into a {@link ReviewReport}, rounding values for display only. */
public final class ReportBuilder {

    private final ReportFormat format;
    private final Clock clock;
    private final ReviewReport.Build build;

    public ReportBuilder(ReportFormat format, Clock clock, ReviewReport.Build build) {
        this.format = format;
        this.clock = clock;
        this.build = build;
    }

    public ReviewReport build(ReviewResult result) {
        var index = result.index();
        var period = result.period();
        var status = result.assessment();

        var constituents = result.constituents().stream()
                .map(c -> new ReviewReport.Constituent(c.ranked().rank(), c.securityId(), c.decision(), c.joiner(),
                        ffmcap(c.weight().ffmcap()), percent(c.weight().rawWeight()), percent(c.weight().weight()),
                        round(c.weight().cappingFactor(), format.cappingFactorDecimals()), c.weight().capped()))
                .toList();
        var joiners = result.joiners().stream()
                .map(c -> new ReviewReport.Joiner(c.securityId(), c.ranked().rank(), c.decision()))
                .toList();
        var leavers = result.leavers().stream()
                .map(l -> new ReviewReport.Leaver(l.securityId(), l.rank(), l.reason(), l.detail()))
                .toList();
        var excluded = result.excluded().stream()
                .map(e -> new ReviewReport.Exclusion(e.securityId(), e.reason()))
                .toList();
        var ranking = result.selection().stream()
                .map(o -> {
                    var security = o.security().security();
                    return new ReviewReport.RankingEntry(o.security().rank(), security.securityId(), security.price(),
                            security.shares(), security.freeFloat(), ffmcap(security.ffmcap()),
                            o.security().incumbent(), o.decision());
                })
                .toList();
        var parameters = new ReviewReport.Parameters(index.methodology(), index.universe(),
                index.constituentCount(), index.directSelectionRank(), index.bufferEndRank(),
                index.weightCap().movePointRight(2).stripTrailingZeros(), index.rankingStrategy());

        return new ReviewReport(index.name(), period.id(), period.cutOffDate(), period.reviewDate(),
                clock.instant(), build, status.status(), status.reasons(), parameters, constituents, joiners, leavers,
                excluded, ranking, result.cappingRounds(), result.input().files(), result.warnings());
    }

    private BigDecimal percent(BigDecimal fraction) {
        return round(fraction.movePointRight(2), format.weightDecimals());
    }

    private BigDecimal ffmcap(BigDecimal ffmcap) {
        return round(ffmcap, format.ffmcapDecimals());
    }

    private static BigDecimal round(BigDecimal value, int decimals) {
        return value.setScale(decimals, RoundingMode.HALF_EVEN);
    }
}
