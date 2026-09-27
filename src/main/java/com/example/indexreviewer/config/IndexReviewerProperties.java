package com.example.indexreviewer.config;

import com.example.indexreviewer.domain.IndexDefinition;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application configuration under {@code index-reviewer}. Index definitions come from
 * {@code config/indices.yml}; {@link IndexDefinition} validates each one, so a bad definition fails at startup.
 *
 * @param dataDir root folder of the input files, holding one folder per index and review period
 * @param reportsDir root folder of stored review reports
 * @param indices configured indices, with unique names
 */
@ConfigurationProperties("index-reviewer")
public record IndexReviewerProperties(Path dataDir, Path reportsDir, List<IndexDefinition> indices) {

    public IndexReviewerProperties {
        Objects.requireNonNull(dataDir, "index-reviewer.data-dir must be set");
        Objects.requireNonNull(reportsDir, "index-reviewer.reports-dir must be set");
        indices = List.copyOf(indices == null ? List.of() : indices);
        var names = new HashSet<String>();
        for (var index : indices) {
            if (!names.add(index.name())) {
                throw new IllegalArgumentException("Duplicate index " + index.name());
            }
        }
    }
}
