package com.example.indexreviewer.config;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.report.ReportFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Application configuration under {@code index-reviewer}. Index definitions come from {@code config/indices.yml}
 * (D11); {@link IndexDefinition} validates each one, so a bad definition fails at startup.
 *
 * @param dataDir root folder of the input files, holding one folder per index and review period (D12)
 * @param reportsDir root folder of stored review reports (D21)
 * @param report  display precision of review reports (D13)
 * @param indices configured indices, with unique names
 */
@ConfigurationProperties("index-reviewer")
public record IndexReviewerProperties(Path dataDir, Path reportsDir, ReportFormat report,
                                      List<IndexDefinition> indices) {

    public IndexReviewerProperties {
        Objects.requireNonNull(dataDir, "index-reviewer.data-dir must be set");
        Objects.requireNonNull(reportsDir, "index-reviewer.reports-dir must be set");
        Objects.requireNonNull(report, "index-reviewer.report.* must be set");
        indices = List.copyOf(indices == null ? List.of() : indices);
        var names = new HashSet<String>();
        for (var index : indices) {
            if (!names.add(index.name())) {
                throw new IllegalArgumentException("Duplicate index " + index.name());
            }
        }
    }
}
