package com.example.indexreviewer.review;

import java.math.BigDecimal;
import java.util.Map;
import java.util.SequencedMap;

/**
 * Decides the maximum weight of each constituent. {@link WeightCapping} then caps iteratively against these
 * limits. The SMI uses one cap for all ({@link SingleCap}); a tiered rule such as the SLI's 9%/4.5% (rulebook
 * 5.17.4) would be another implementation.
 */
interface CappingRule {

    /**
     * @param ffmcapById FFMCAP of each selected constituent, in rank order
     * @return maximum weight of each constituent, in (0, 1]
     */
    Map<String, BigDecimal> caps(SequencedMap<String, BigDecimal> ffmcapById);
}
