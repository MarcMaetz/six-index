package com.example.indexreviewer.review;

import com.example.indexreviewer.domain.RankingStrategy;
import java.math.BigDecimal;

/**
 * A universe security with all data a review needs, and its free float market capitalization
 * {@code FFMCAP = price(t') × shares(t) × free float(t)}.
 */
public record EligibleSecurity(
        String securityId, BigDecimal price, long shares, BigDecimal freeFloat, BigDecimal ffmcap) {

    public static EligibleSecurity of(String securityId, BigDecimal price, long shares, BigDecimal freeFloat) {
        // Multiplication only, so the product is exact without a MathContext.
        BigDecimal ffmcap = price.multiply(BigDecimal.valueOf(shares)).multiply(freeFloat);
        return new EligibleSecurity(securityId, price, shares, freeFloat, ffmcap);
    }

    /** The value this security is ranked by under the given strategy; higher ranks higher. */
    public BigDecimal rankingValue(RankingStrategy strategy) {
        return switch (strategy) {
            case FFMCAP -> ffmcap;
        };
    }
}
