package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;
import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.review.Leaver.LeaveReason;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs an ordinary index review: eligibility → ranking → buffer selection → weight capping, then derives
 * joiners and leavers against the current composition. Stateless and framework-free.
 */
public final class ReviewEngine {

    static final String SOURCE = "review";

    public ReviewResult run(IndexDefinition index, ReviewPeriod period, InputData input) {
        var eligibility = Eligibility.check(input, period);
        var warnings = new ArrayList<>(input.warnings());
        warnings.addAll(eligibility.excluded().stream()
                .map(exclusion -> new DataQualityWarning(SOURCE, null, Impact.MISSING_DATA,
                        "Excluded from ranking: " + exclusion.reason(), List.of(exclusion.securityId())))
                .toList());

        var ranked = Ranking.rank(eligibility.eligible(), index.rankingStrategy(), input.currentComposition());
        requireEnoughRanked(index, ranked.size(), eligibility.excluded().size());
        var selection = Selection.select(ranked, index);
        var selected = selection.stream().filter(o -> o.decision().selected()).toList();

        var ffmcapById = new LinkedHashMap<String, BigDecimal>();
        for (var outcome : selected) {
            ffmcapById.put(outcome.securityId(), outcome.ranked().eligible().ffmcap());
        }
        var capping = WeightCapping.cap(ffmcapById, index.weightCap());
        Map<String, CappedWeight> weightById = capping.weights().stream()
                .collect(Collectors.toMap(CappedWeight::securityId, Function.identity()));

        var constituents = selected.stream()
                .map(o -> new Constituent(o, weightById.get(o.securityId())))
                .toList();

        return new ReviewResult(index, period, input, eligibility.excluded(), selection, constituents,
                leavers(input, period, eligibility, selection), capping.rounds(), warnings);
    }

    /**
     * A12: an index with fewer constituents than its definition is not that index, and its caps might not even add
     * up to 100%, so the review stops instead of publishing it.
     */
    private static void requireEnoughRanked(IndexDefinition index, int ranked, int excluded) {
        if (ranked < index.constituentCount()) {
            throw new IncompleteIndexException(("%s needs %d constituents, but only %d securities could be ranked; "
                    + "%d universe securities were excluded for missing data")
                    .formatted(index.name(), index.constituentCount(), ranked, excluded));
        }
    }

    /** Current constituents not selected, in the order of the current composition. */
    private static List<Leaver> leavers(InputData input, ReviewPeriod period, Eligibility.Result eligibility,
                                        List<Selection.Outcome> selection) {
        Map<String, Selection.Outcome> outcomeById = selection.stream()
                .collect(Collectors.toMap(Selection.Outcome::securityId, Function.identity()));
        Map<String, Exclusion> exclusionById = eligibility.excluded().stream()
                .collect(Collectors.toMap(Exclusion::securityId, Function.identity()));

        var leavers = new ArrayList<Leaver>();
        for (String id : input.currentComposition()) {
            var outcome = outcomeById.get(id);
            if (outcome != null) {
                if (!outcome.decision().selected()) {
                    leavers.add(notSelected(outcome));
                }
            } else if (exclusionById.containsKey(id)) {
                leavers.add(new Leaver(id, null, LeaveReason.NOT_ELIGIBLE, exclusionById.get(id).reason()));
            } else {
                // A universe security is either ranked or excluded, so this one left the universe.
                leavers.add(new Leaver(id, null, LeaveReason.NOT_IN_UNIVERSE,
                        "Not in the universe on " + period.reviewDate()));
            }
        }
        return leavers;
    }

    private static Leaver notSelected(Selection.Outcome outcome) {
        int rank = outcome.rank();
        boolean bufferFull = outcome.decision() == SelectionDecision.NOT_SELECTED_BUFFER_FULL;
        return new Leaver(outcome.securityId(), rank,
                bufferFull ? LeaveReason.BUFFER_FULL : LeaveReason.BELOW_BUFFER,
                "Rank %d, %s".formatted(rank, bufferFull ? "buffer slots taken" : "below the buffer"));
    }
}
