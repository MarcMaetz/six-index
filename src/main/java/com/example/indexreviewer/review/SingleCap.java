package com.example.indexreviewer.review;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;

/** The same maximum weight for every constituent, e.g. 18% for the SMI (rulebook 5.12.4). */
record SingleCap(BigDecimal cap) implements CappingRule {

    @Override
    public Map<String, BigDecimal> caps(SequencedMap<String, BigDecimal> ffmcapById) {
        var caps = new LinkedHashMap<String, BigDecimal>();
        ffmcapById.keySet().forEach(id -> caps.put(id, cap));
        return caps;
    }
}
