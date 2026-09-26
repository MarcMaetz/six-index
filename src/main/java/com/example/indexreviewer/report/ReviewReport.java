package com.example.indexreviewer.report;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.InputFile;
import com.example.indexreviewer.review.Leaver.LeaveReason;
import com.example.indexreviewer.review.ReviewStatus;
import com.example.indexreviewer.review.SelectionDecision;
import com.example.indexreviewer.review.StatusReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The result of an index review as delivered to its users: the new composition with weights, joiners, leavers
 * and status, plus the audit trail behind them (parameters, input files, full ranking, capping rounds,
 * warnings). Values are rounded for display (D13); weights are in percent.
 */
public record ReviewReport(
        String index,
        String reviewPeriod,
        LocalDate cutOffDate,
        LocalDate reviewDate,
        Instant generatedAt,
        ReviewStatus status,
        List<StatusReason> statusReasons,
        Parameters parameters,
        List<Constituent> constituents,
        List<Joiner> joiners,
        List<Leaver> leavers,
        List<Exclusion> excluded,
        List<RankingEntry> ranking,
        List<List<String>> cappingRounds,
        List<InputFile> inputFiles,
        List<DataQualityWarning> warnings) {

    /** The index parameters the review ran with. */
    public record Parameters(String universe, int constituentCount, int directSelectionRank, int bufferEndRank,
                             BigDecimal weightCapPercent, String rankingStrategy) {
    }

    public record Constituent(int rank, String securityId, SelectionDecision selection, boolean joiner,
                              BigDecimal ffmcap, BigDecimal rawWeightPercent, BigDecimal weightPercent,
                              BigDecimal cappingFactor, boolean capped) {
    }

    public record Joiner(String securityId, int rank, SelectionDecision selection) {
    }

    public record Leaver(String securityId, Integer rank, LeaveReason reason, String detail) {
    }

    public record Exclusion(String securityId, String reason) {
    }

    /** One line of the full ranking, with the selection decision taken for it. */
    public record RankingEntry(int rank, String securityId, BigDecimal ffmcap, boolean incumbent,
                               SelectionDecision selection) {
    }
}
