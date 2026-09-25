package com.example.indexreviewer.config;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.ingest.InputDataLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Looks up configured indices and review periods, and loads the input of a review from its folder
 * {@code <data-dir>/<index>/<period>} (D12).
 */
@Component
public class IndexCatalog {

    private final IndexReviewerProperties properties;
    private final InputDataLoader loader = new InputDataLoader();

    public IndexCatalog(IndexReviewerProperties properties) {
        this.properties = properties;
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

    public Path inputDir(IndexDefinition index, ReviewPeriod period) {
        return properties.dataDir().resolve(index.name()).resolve(period.id()).normalize();
    }

    public InputData loadInput(IndexDefinition index, ReviewPeriod period) {
        return loader.load(inputDir(index, period), index.universe());
    }
}
