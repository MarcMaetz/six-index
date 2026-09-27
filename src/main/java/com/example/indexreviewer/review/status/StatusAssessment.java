package com.example.indexreviewer.review.status;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;
import com.example.indexreviewer.review.RankedSecurity;
import com.example.indexreviewer.review.ReviewResult;
import com.example.indexreviewer.review.Selection;
import com.example.indexreviewer.review.status.StatusReason.Relevance;
import com.example.indexreviewer.review.status.StatusReason.WarningRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derives the review status from the warnings. Every warning yields a {@link StatusReason} per
 * affected security, with a {@link Relevance}: missing data matters when it names no security, or a security
 * that could change the result: a current constituent, a security ranked within the buffer end, or an unranked
 * security that could rank there ({@link UnrankedEstimate}, A13). An index with fewer constituents than needed
 * always needs attention (A12).
 * <p>
 * Part of the review, not the report: it judges the result using the index's buffer and ranking strategy.
 */
public final class StatusAssessment {

    /** The review status and every reason behind it, harmless ones included. */
    public record Result(ReviewStatus status, List<StatusReason> reasons) {
    }

    private final ReviewResult result;
    private final int bufferEnd;
    private final Map<String, RankedSecurity> rankedById;
    private final UnrankedEstimate unranked;

    private StatusAssessment(ReviewResult result) {
        this.result = result;
        this.bufferEnd = result.index().bufferEndRank();
        var ranked = result.selection().stream().map(Selection.Outcome::ranked).toList();
        this.rankedById = ranked.stream()
                .collect(Collectors.toMap(RankedSecurity::securityId, Function.identity()));
        this.unranked = new UnrankedEstimate(result, ranked);
    }

    public static Result assess(ReviewResult result) {
        return new StatusAssessment(result).assess();
    }

    private Result assess() {
        var reasons = new ArrayList<StatusReason>();
        for (var warning : result.warnings()) {
            reasons.addAll(reasons(warning));
        }
        int selected = result.constituents().size();
        int needed = result.index().constituentCount();
        if (selected < needed) {
            reasons.add(new StatusReason(null, Relevance.INDEX_INCOMPLETE,
                    "Only %d securities could be selected, %d needed".formatted(selected, needed), null));
        }
        return new Result(ReviewStatus.of(reasons), reasons);
    }

    /** One reason per affected security, or one without a security if the warning names none. */
    private List<StatusReason> reasons(DataQualityWarning warning) {
        var ref = WarningRef.of(warning);
        if (warning.impact() == Impact.NONE) {
            return List.of(new StatusReason(null, Relevance.NO_DATA_LOST, "No data lost", ref));
        }
        if (warning.securityIds().isEmpty()) {
            return List.of(new StatusReason(null, Relevance.SECURITY_UNKNOWN,
                    "Data is missing, but the affected security is unknown", ref));
        }
        return warning.securityIds().stream().map(id -> reason(id, ref)).toList();
    }

    /** How missing data on one security relates to the result. */
    private StatusReason reason(String id, WarningRef warning) {
        if (result.input().currentComposition().contains(id)) {
            return new StatusReason(id, Relevance.CURRENT_CONSTITUENT, "Current constituent", warning);
        }
        var rankedSecurity = rankedById.get(id);
        if (rankedSecurity == null) {
            return unranked.judge(id, warning);
        }
        int rank = rankedSecurity.rank();
        return rank <= bufferEnd
                ? new StatusReason(id, Relevance.RANKED_WITHIN_BUFFER,
                        "Ranked %d, within buffer end %d".formatted(rank, bufferEnd), warning)
                : new StatusReason(id, Relevance.RANKED_BELOW_BUFFER,
                        "Ranked %d, below buffer end %d".formatted(rank, bufferEnd), warning);
    }
}
