package com.example.indexreviewer.review;

import java.math.BigDecimal;

/**
 * An eligible security with its rank (1 = highest ranking value).
 *
 * @param incumbent whether it is in the current index composition
 */
public record RankedSecurity(int rank, EligibleSecurity eligible, BigDecimal rankingValue, boolean incumbent) {

    public String securityId() {
        return eligible.securityId();
    }
}
