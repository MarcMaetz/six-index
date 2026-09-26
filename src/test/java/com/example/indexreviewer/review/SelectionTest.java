package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.IndexDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.example.indexreviewer.review.SelectionDecision.NOT_SELECTED_BELOW_BUFFER;
import static com.example.indexreviewer.review.SelectionDecision.NOT_SELECTED_BUFFER_FULL;
import static com.example.indexreviewer.review.SelectionDecision.SELECTED_BUFFER_INCUMBENT;
import static com.example.indexreviewer.review.SelectionDecision.SELECTED_BUFFER_NEW;
import static com.example.indexreviewer.review.SelectionDecision.SELECTED_DIRECT;
import static org.assertj.core.api.Assertions.assertThat;

/** A small index: 5 constituents, ranks 1–3 direct, buffer 4–7. */
class SelectionTest {

    private static final IndexDefinition INDEX =
            new IndexDefinition("TEST", "Rulebook v3.40", "SPI", 5, 3, 7, new BigDecimal("0.5"), "FFMCAP", List.of());

    @Test
    void incumbentsInBufferHavePriorityOverHigherRankedNewCandidates() {
        // Ranks 4 and 5 are new, 6 and 7 incumbents: the incumbents fill the two free slots.
        var decisions = decisions(8, Set.of(6, 7));

        assertThat(decisions).containsExactly(SELECTED_DIRECT, SELECTED_DIRECT, SELECTED_DIRECT,
                NOT_SELECTED_BUFFER_FULL, NOT_SELECTED_BUFFER_FULL,
                SELECTED_BUFFER_INCUMBENT, SELECTED_BUFFER_INCUMBENT, NOT_SELECTED_BELOW_BUFFER);
    }

    @Test
    void newCandidatesFillSlotsLeftAfterIncumbentsInRankOrder() {
        // One incumbent in the buffer (rank 6); the remaining slot goes to the best new candidate, rank 4.
        var decisions = decisions(8, Set.of(6));

        assertThat(decisions).containsExactly(SELECTED_DIRECT, SELECTED_DIRECT, SELECTED_DIRECT,
                SELECTED_BUFFER_NEW, NOT_SELECTED_BUFFER_FULL, SELECTED_BUFFER_INCUMBENT,
                NOT_SELECTED_BUFFER_FULL, NOT_SELECTED_BELOW_BUFFER);
    }

    @Test
    void incumbentsInBufferCompeteByRankWhenTheyOutnumberSlots() {
        var decisions = decisions(7, Set.of(4, 5, 6));

        assertThat(decisions).containsExactly(SELECTED_DIRECT, SELECTED_DIRECT, SELECTED_DIRECT,
                SELECTED_BUFFER_INCUMBENT, SELECTED_BUFFER_INCUMBENT, NOT_SELECTED_BUFFER_FULL,
                NOT_SELECTED_BUFFER_FULL);
    }

    @Test
    void takesAllRankedWhenFewerThanNeeded() {
        assertThat(decisions(4, Set.of())).containsExactly(
                SELECTED_DIRECT, SELECTED_DIRECT, SELECTED_DIRECT, SELECTED_BUFFER_NEW);
    }

    private static List<SelectionDecision> decisions(int count, Set<Integer> incumbentRanks) {
        return Selection.select(ranked(count, incumbentRanks), INDEX).stream()
                .map(Selection.Outcome::decision).toList();
    }

    private static List<RankedSecurity> ranked(int count, Set<Integer> incumbentRanks) {
        var ranked = new ArrayList<RankedSecurity>();
        for (int rank = 1; rank <= count; rank++) {
            var value = BigDecimal.valueOf(1000 - rank);
            var security = new EligibleSecurity("S" + rank, BigDecimal.ONE, 1, BigDecimal.ONE, value);
            ranked.add(new RankedSecurity(rank, security, value, incumbentRanks.contains(rank)));
        }
        return ranked;
    }
}
