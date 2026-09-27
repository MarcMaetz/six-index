package com.example.indexreviewer.api;

import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.Leaver;
import com.example.indexreviewer.review.status.ReviewStatus;
import com.example.indexreviewer.review.status.StatusReason;
import com.example.indexreviewer.store.StoredReport;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * The outcome of a review run: status, new composition and changes. The full report with the audit trail
 * (ranking, capping rounds, input files, warnings) is stored and read from {@code reportUrl}.
 *
 * @param reportUrl where to get the full stored report; the same URI as the {@code Location} header, in the
 *                  body too because clients and tools that only read the JSON would miss the header
 */
public record ReviewSummaryResponse(
        String reportId,
        URI reportUrl,
        String index,
        String reviewPeriod,
        Instant generatedAt,
        ReviewStatus status,
        List<StatusReason> statusReasons,
        List<Constituent> constituents,
        List<ReviewReport.Joiner> joiners,
        List<Leaver> leavers) {

    /** A security in the new composition; weights are in percent. */
    public record Constituent(int rank, String securityId, boolean joiner, BigDecimal weightPercent,
                              boolean capped) {
    }

    static ReviewSummaryResponse of(StoredReport stored, URI reportUrl, ReviewReport report) {
        var constituents = report.constituents().stream()
                .map(c -> new Constituent(c.rank(), c.securityId(), c.joiner(), c.weightPercent(), c.capped()))
                .toList();
        return new ReviewSummaryResponse(stored.id(), reportUrl, report.index(), report.reviewPeriod(), report.generatedAt(),
                report.status(), report.statusReasons(), constituents, report.joiners(), report.leavers());
    }
}
