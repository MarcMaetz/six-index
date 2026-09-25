package com.example.indexreviewer.config;

import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.store.FileReportStore;
import com.example.indexreviewer.store.ReportStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/** Makes the framework-free review engine, report builder and report store available as beans. */
@Configuration
public class ReviewConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ReviewEngine reviewEngine() {
        return new ReviewEngine();
    }

    @Bean
    ReportBuilder reportBuilder(IndexReviewerProperties properties, Clock clock) {
        return new ReportBuilder(properties.report(), clock);
    }

    /** Uses the application's JSON mapper, so stored reports look exactly like the API's responses. */
    @Bean
    ReportStore reportStore(IndexReviewerProperties properties, JsonMapper jsonMapper) {
        return new FileReportStore(properties.reportsDir(), jsonMapper);
    }
}
