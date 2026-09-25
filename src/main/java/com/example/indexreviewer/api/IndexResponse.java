package com.example.indexreviewer.api;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.ReviewPeriod;

import java.math.BigDecimal;
import java.util.List;

/** A configured index and its review periods. */
public record IndexResponse(
        String name,
        String universe,
        int constituentCount,
        int directSelectionRank,
        int bufferEndRank,
        BigDecimal weightCap,
        String rankingStrategy,
        List<ReviewPeriod> reviewPeriods) {

    static IndexResponse of(IndexDefinition index) {
        return new IndexResponse(index.name(), index.universe(), index.constituentCount(),
                index.directSelectionRank(), index.bufferEndRank(), index.weightCap(), index.rankingStrategy(),
                index.reviewPeriods());
    }
}
