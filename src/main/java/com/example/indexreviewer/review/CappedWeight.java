package com.example.indexreviewer.review;

import java.math.BigDecimal;

/**
 * The weight of one constituent, before and after capping, at full precision (D13).
 *
 * @param rawWeight     FFMCAP share of the total, before capping
 * @param weight        final weight, at most the cap
 * @param cappingFactor factor applied to FFMCAP to reach the final weight, normalised so the largest factor is 1
 * @param capped        whether the constituent was limited to the cap
 */
public record CappedWeight(String securityId, BigDecimal ffmcap, BigDecimal rawWeight, BigDecimal weight,
                           BigDecimal cappingFactor, boolean capped) {
}
