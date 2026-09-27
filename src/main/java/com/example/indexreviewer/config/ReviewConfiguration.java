package com.example.indexreviewer.config;

import com.example.indexreviewer.catalog.IndexCatalog;
import com.example.indexreviewer.ingest.CsvFolderInputSource;
import com.example.indexreviewer.ingest.InputSource;
import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.store.FileReportStore;
import com.example.indexreviewer.store.ReportStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Makes the framework-free catalog, input source, review engine, report builder and report store available as
 * beans.
 */
@Configuration
public class ReviewConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** The indices of {@code config/indices.yml}. */
    @Bean
    IndexCatalog indexCatalog(IndexReviewerProperties properties) {
        return new IndexCatalog(properties.indices());
    }

    @Bean
    ReviewEngine reviewEngine() {
        return new ReviewEngine();
    }

    @Bean
    ReportBuilder reportBuilder(IndexReviewerProperties properties, Clock clock,
                                ObjectProvider<BuildProperties> buildProperties,
                                ObjectProvider<GitProperties> gitProperties) {
        return new ReportBuilder(clock,
                build(buildProperties.getIfAvailable(), gitProperties.getIfAvailable()));
    }

    /**
     * Version from {@code build-info.properties}, revision from {@code git.properties}, both written by Gradle.
     * {@code -dirty} marks a build with uncommitted changes; a missing file gives {@code unknown}.
     */
    static ReviewReport.Build build(BuildProperties buildProperties, GitProperties gitProperties) {
        String version = buildProperties == null ? "unknown" : buildProperties.getVersion();
        String revision = gitProperties == null || gitProperties.getShortCommitId() == null
                ? "unknown"
                : gitProperties.getShortCommitId() + ("true".equals(gitProperties.get("dirty")) ? "-dirty" : "");
        return new ReviewReport.Build(version, revision);
    }

    /** Input from the CSV folder per index and review period. */
    @Bean
    InputSource inputSource(IndexReviewerProperties properties) {
        return new CsvFolderInputSource(properties.dataDir());
    }

    /** Uses the application's JSON mapper, so stored reports look exactly like the API's responses. */
    @Bean
    ReportStore reportStore(IndexReviewerProperties properties, JsonMapper jsonMapper) {
        return new FileReportStore(properties.reportsDir(), jsonMapper);
    }
}
