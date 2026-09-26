package com.example.indexreviewer.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IndexDefinitionTest {

    private static final ReviewPeriod Q3 =
            new ReviewPeriod("2026-Q3", LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-21"));

    @Test
    void acceptsSmiDefinition() {
        var smi = smi(20, 18, 22, "0.18", List.of(Q3));

        assertThat(smi.reviewPeriod("2026-Q3")).contains(Q3);
        assertThat(smi.reviewPeriod("2026-Q4")).isEmpty();
    }

    @Test
    void rejectsInconsistentRanks() {
        assertThatIllegalArgumentException().isThrownBy(() -> smi(20, 21, 22, "0.18", List.of()))
                .withMessageContaining("direct selection rank 21");
        assertThatIllegalArgumentException().isThrownBy(() -> smi(20, 18, 19, "0.18", List.of()))
                .withMessageContaining("buffer end rank 19");
    }

    @Test
    void rejectsCapThatCannotAddUpToFullWeight() {
        assertThatIllegalArgumentException().isThrownBy(() -> smi(20, 18, 22, "0.04", List.of()))
                .withMessageContaining("cannot add up to 100%");
        assertThatIllegalArgumentException().isThrownBy(() -> smi(20, 18, 22, "1.5", List.of()))
                .withMessageContaining("must be in (0, 1]");
    }

    @Test
    void requiresMethodology() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                        new IndexDefinition("SMI", " ", "SPI", 20, 18, 22, new BigDecimal("0.18"), "FFMCAP", List.of()))
                .withMessageContaining("methodology of SMI must not be blank");
    }

    @Test
    void rejectsDuplicateReviewPeriods() {
        assertThatIllegalArgumentException().isThrownBy(() -> smi(20, 18, 22, "0.18", List.of(Q3, Q3)))
                .withMessageContaining("duplicate review period 2026-Q3");
    }

    @Test
    void rejectsCutOffAfterReviewDate() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                        new ReviewPeriod("2026-Q3", LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-21")))
                .withMessageContaining("is after review date");
    }

    private static IndexDefinition smi(int count, int direct, int bufferEnd, String cap, List<ReviewPeriod> periods) {
        return new IndexDefinition("SMI", "Rulebook v3.40", "SPI", count, direct, bufferEnd, new BigDecimal(cap),
                "FFMCAP", periods);
    }
}
