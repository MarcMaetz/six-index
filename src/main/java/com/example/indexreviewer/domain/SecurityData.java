package com.example.indexreviewer.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Market data of one security on one date, as delivered in {@code sec_data.csv}.
 * <p>
 * Each value is {@code null} when the source leaves it empty: prices are only delivered for the cut-off date,
 * shares and free float are needed for the review date (A3). Which values a review needs is decided by the
 * review, not here.
 *
 * @param price     closing price, positive, or {@code null}
 * @param freeFloat free-float factor in (0, 1], or {@code null}
 * @param shares    number of shares outstanding, positive, or {@code null}
 */
public record SecurityData(String securityId, LocalDate date, BigDecimal price, BigDecimal freeFloat, Long shares) {

    public SecurityData {
        Objects.requireNonNull(securityId, "securityId");
        Objects.requireNonNull(date, "date");
    }

    /** Compares values numerically, so {@code 1.5} and {@code 1.50} count as the same price. */
    public boolean sameValuesAs(SecurityData other) {
        return securityId.equals(other.securityId)
                && date.equals(other.date)
                && numericallyEqual(price, other.price)
                && numericallyEqual(freeFloat, other.freeFloat)
                && Objects.equals(shares, other.shares);
    }

    private static boolean numericallyEqual(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
