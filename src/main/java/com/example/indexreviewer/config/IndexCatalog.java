package com.example.indexreviewer.config;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.review.RankingStrategies;
import org.springframework.stereotype.Component;

import java.util.List;

/** Looks up configured indices and review periods. Loading their input is up to the {@code InputSource} (D28). */
@Component
public class IndexCatalog {

    private final IndexReviewerProperties properties;

    public IndexCatalog(IndexReviewerProperties properties) {
        this.properties = properties;
        // Fail at startup, not at the first review, if an index names a ranking strategy that doesn't exist.
        properties.indices().forEach(index -> RankingStrategies.byName(index.rankingStrategy()));
    }

    public List<IndexDefinition> indices() {
        return properties.indices();
    }

    public IndexDefinition index(String name) {
        return properties.indices().stream()
                .filter(index -> index.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new NotConfiguredException("Index " + name + " is not configured"));
    }

    public ReviewPeriod reviewPeriod(IndexDefinition index, String periodId) {
        return index.reviewPeriod(periodId).orElseThrow(() -> new NotConfiguredException(
                "Review period " + periodId + " is not configured for " + index.name()));
    }
}
