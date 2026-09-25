package com.example.indexreviewer.review;

import java.math.BigDecimal;

/**
 * A universe security with all data a review needs, and its free float market capitalization
 * {@code FFMCAP = price(t') × shares(t) × free float(t)}.
 */
public record EligibleSecurity(String securityId, BigDecimal price, long shares, BigDecimal freeFloat,
                               BigDecimal ffmcap) {

    static EligibleSecurity of(String securityId, BigDecimal price, long shares, BigDecimal freeFloat) {
        // Multiplication only, so the product is exact without a MathContext (D13).
        BigDecimal ffmcap = price.multiply(BigDecimal.valueOf(shares)).multiply(freeFloat);
        return new EligibleSecurity(securityId, price, shares, freeFloat, ffmcap);
    }
}
