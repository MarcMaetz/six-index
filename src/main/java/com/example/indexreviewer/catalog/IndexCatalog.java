package com.example.indexreviewer.catalog;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.review.RankingStrategies;

import java.util.List;

/**
 * Looks up configured indices and review periods. Loading their input is up to the {@code InputSource}.
 * Framework-free: {@code ReviewConfiguration} builds it from the bound configuration.
 */
public final class IndexCatalog {

    private final List<IndexDefinition> indices;

    public IndexCatalog(List<IndexDefinition> indices) {
        this.indices = List.copyOf(indices);
        // Fail at startup, not at the first review, if an index names a ranking strategy that doesn't exist.
        this.indices.forEach(index -> RankingStrategies.byName(index.rankingStrategy()));
    }

    public List<IndexDefinition> indices() {
        return indices;
    }

    public IndexDefinition index(String name) {
        return indices.stream()
                .filter(index -> index.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new NotConfiguredException("Index " + name + " is not configured"));
    }

    public ReviewPeriod reviewPeriod(IndexDefinition index, String periodId) {
        return index.reviewPeriod(periodId).orElseThrow(() -> new NotConfiguredException(
                "Review period " + periodId + " is not configured for " + index.name()));
    }
}
