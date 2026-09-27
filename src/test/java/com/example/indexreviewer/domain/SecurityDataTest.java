package com.example.indexreviewer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SecurityDataTest {

    private static final LocalDate DATE = LocalDate.parse("2026-09-10");

    @Test
    void sameValuesCompareNumericallyAndTreatMissingAsAValue() {
        var data = data("1", DATE, "1.5", null, 100L);

        assertThat(data.sameValuesAs(data("1", DATE, "1.50", null, 100L))).isTrue();
        assertThat(data.sameValuesAs(data("1", DATE, "1.5", "0.5", 100L))).isFalse();
        assertThat(data.sameValuesAs(data("1", DATE, null, null, 100L))).isFalse();
        assertThat(data.sameValuesAs(data("1", DATE, "1.6", null, 100L))).isFalse();
        assertThat(data.sameValuesAs(data("1", DATE, "1.5", null, 101L))).isFalse();
        assertThat(data.sameValuesAs(data("2", DATE, "1.5", null, 100L))).isFalse();
        assertThat(data.sameValuesAs(data("1", DATE.plusDays(1), "1.5", null, 100L)))
                .isFalse();
    }

    private static SecurityData data(String id, LocalDate date, String price, String freeFloat, Long shares) {
        return new SecurityData(
                id,
                date,
                price == null ? null : new BigDecimal(price),
                freeFloat == null ? null : new BigDecimal(freeFloat),
                shares);
    }
}
