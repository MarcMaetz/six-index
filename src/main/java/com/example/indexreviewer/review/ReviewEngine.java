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
        var warnings = new ArrayList<>(input.warnings());

        var eligibility = Eligibility.check(input, period);
        for (var exclusion : eligibility.excluded()) {
            warnings.add(new DataQualityWarning(SOURCE, null, Impact.MISSING_DATA,
                    "Excluded from ranking: " + exclusion.reason(), List.of(exclusion.securityId())));
        }

        var ranked = Ranking.rank(eligibility.eligible(), RankingStrategies.byName(index.rankingStrategy()),
                input.currentComposition());
        var selection = Selection.select(ranked, index);
        var selected = selection.stream().filter(o -> o.decision().selected()).toList();

        var ffmcapById = new LinkedHashMap<String, BigDecimal>();
        selected.forEach(o -> ffmcapById.put(o.security().securityId(), o.security().security().ffmcap()));
        var capping = WeightCapping.cap(ffmcapById, index.weightCap());
        Map<String, CappedWeight> weightById = capping.weights().stream()
                .collect(Collectors.toMap(CappedWeight::securityId, Function.identity()));

        var constituents = selected.stream()
                .map(o -> new Constituent(o.security(), o.decision(), !o.security().incumbent(),
                        weightById.get(o.security().securityId())))
                .toList();

        return new ReviewResult(index, period, input, eligibility.excluded(), selection, constituents,
                leavers(input, period, eligibility, selection), capping.rounds(), warnings);
    }

    /** Current constituents not selected, in the order of the current composition. */
    private static List<Leaver> leavers(InputData input, ReviewPeriod period, Eligibility.Result eligibility,
                                        List<Selection.Outcome> selection) {
        Map<String, Selection.Outcome> outcomeById = selection.stream()
                .collect(Collectors.toMap(o -> o.security().securityId(), Function.identity()));
        Map<String, Exclusion> exclusionById = eligibility.excluded().stream()
                .collect(Collectors.toMap(Exclusion::securityId, Function.identity()));
        var universe = input.universe(period.reviewDate());

        var leavers = new ArrayList<Leaver>();
        for (String id : input.currentComposition()) {
            var outcome = outcomeById.get(id);
            if (outcome != null) {
                if (!outcome.decision().selected()) {
                    int rank = outcome.security().rank();
                    boolean bufferFull = outcome.decision() == SelectionDecision.NOT_SELECTED_BUFFER_FULL;
                    leavers.add(new Leaver(id, rank,
                            bufferFull ? LeaveReason.BUFFER_FULL : LeaveReason.BELOW_BUFFER,
                            "Rank %d, %s".formatted(rank, bufferFull ? "buffer slots taken" : "below the buffer")));
                }
            } else if (exclusionById.containsKey(id)) {
                leavers.add(new Leaver(id, null, LeaveReason.NOT_ELIGIBLE, exclusionById.get(id).reason()));
            } else if (!universe.contains(id)) {
                leavers.add(new Leaver(id, null, LeaveReason.NOT_IN_UNIVERSE,
                        "Not in the universe on " + period.reviewDate()));
            }
        }
        return leavers;
    }
}
