package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;

import java.util.List;

/**
 * Everything a review decided, with the intermediate steps kept for traceability.
 *
 * @param excluded       universe securities that could not be ranked
 * @param selection      decision for every ranked security, in rank order
 * @param constituents   the new composition, in rank order
 * @param leavers        current constituents that leave
 * @param cappingRounds  ids capped in each capping round
 * @param warnings       input warnings followed by warnings raised by the review
 */
public record ReviewResult(
        IndexDefinition index,
        ReviewPeriod period,
        InputData input,
        List<Exclusion> excluded,
        List<Selection.Outcome> selection,
        List<Constituent> constituents,
        List<Leaver> leavers,
        List<List<String>> cappingRounds,
        List<DataQualityWarning> warnings) {

    public ReviewResult {
        excluded = List.copyOf(excluded);
        selection = List.copyOf(selection);
        constituents = List.copyOf(constituents);
        leavers = List.copyOf(leavers);
        cappingRounds = List.copyOf(cappingRounds);
        warnings = List.copyOf(warnings);
    }

    public List<Constituent> joiners() {
        return constituents.stream().filter(Constituent::joiner).toList();
    }

    /** The review status with its reasons, derived from the warnings and the result (D27). */
    public StatusAssessment.Result assessment() {
        return StatusAssessment.assess(this);
    }
}
