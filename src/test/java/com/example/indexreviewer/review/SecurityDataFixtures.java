package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.SecurityData;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Builds {@link SecurityData} for tests from decimal strings; {@code null} stands for an empty value. */
final class SecurityDataFixtures {

    private SecurityDataFixtures() {
    }

    static SecurityData securityData(String id, LocalDate date, String price, String freeFloat, Long shares) {
        return new SecurityData(id, date, price == null ? null : new BigDecimal(price),
                freeFloat == null ? null : new BigDecimal(freeFloat), shares);
    }
}
