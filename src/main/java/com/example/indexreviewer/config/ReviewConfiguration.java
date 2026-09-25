package com.example.indexreviewer.config;

import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.review.ReviewEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Makes the framework-free review engine and report builder available as beans. */
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
}
