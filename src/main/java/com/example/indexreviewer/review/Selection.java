package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.IndexDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buffer selection (rulebook 5.12.3.2): ranks up to the direct selection rank are selected; from the buffer
 * (up to the buffer end rank) current constituents are taken first, then new candidates, each in rank order
 * (A7), until the index has its constituent count. Every ranked security gets a decision, for traceability.
 * <p>
 * Since the buffer end is at least the constituent count, the buffer always has enough candidates unless fewer
 * securities are ranked than the index needs; then all are selected and the engine warns (A12).
 */
public final class Selection {

    public record Outcome(RankedSecurity security, SelectionDecision decision) {
    }

    private Selection() {
    }

    /** Decisions for all ranked securities, in rank order. */
    public static List<Outcome> select(List<RankedSecurity> ranked, IndexDefinition index) {
        Map<RankedSecurity, SelectionDecision> decisions = new LinkedHashMap<>();
        var buffer = new ArrayList<RankedSecurity>();
        for (var security : ranked) {
            if (security.rank() <= index.directSelectionRank()) {
                decisions.put(security, SelectionDecision.SELECTED_DIRECT);
            } else if (security.rank() <= index.bufferEndRank()) {
                decisions.put(security, SelectionDecision.NOT_SELECTED_BUFFER_FULL);
                buffer.add(security);
            } else {
                decisions.put(security, SelectionDecision.NOT_SELECTED_BELOW_BUFFER);
            }
        }

        int slots = index.constituentCount() - countSelected(decisions);
        for (boolean incumbents : new boolean[]{true, false}) {
            for (var security : buffer) {
                if (slots > 0 && security.incumbent() == incumbents) {
                    decisions.put(security, incumbents
                            ? SelectionDecision.SELECTED_BUFFER_INCUMBENT
                            : SelectionDecision.SELECTED_BUFFER_NEW);
                    slots--;
                }
            }
        }
        return decisions.entrySet().stream().map(e -> new Outcome(e.getKey(), e.getValue())).toList();
    }

    private static int countSelected(Map<RankedSecurity, SelectionDecision> decisions) {
        return (int) decisions.values().stream().filter(SelectionDecision::selected).count();
    }
}
