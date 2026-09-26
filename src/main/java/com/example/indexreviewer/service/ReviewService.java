package com.example.indexreviewer.service;

import com.example.indexreviewer.config.IndexCatalog;
import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.store.ReportStore;
import com.example.indexreviewer.store.StoredReport;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The application's use cases, addressed by index name and review period id (D24). Any entry point (the REST API
 * today, a scheduler or CLI later) calls these instead of chaining catalog, engine, report builder and store itself.
 */
@Service
public class ReviewService {

    /** The input of a review, loaded and validated, with the index and period it was loaded for. */
    public record ReviewInput(IndexDefinition index, ReviewPeriod period, InputData data) {
    }

    /** A review that was run and stored. */
    public record ReviewRun(StoredReport stored, ReviewReport report) {
    }

    private final IndexCatalog catalog;
    private final ReviewEngine engine;
    private final ReportBuilder reportBuilder;
    private final ReportStore reportStore;

    public ReviewService(IndexCatalog catalog, ReviewEngine engine, ReportBuilder reportBuilder,
                         ReportStore reportStore) {
        this.catalog = catalog;
        this.engine = engine;
        this.reportBuilder = reportBuilder;
        this.reportStore = reportStore;
    }

    public List<IndexDefinition> indices() {
        return catalog.indices();
    }

    /** Loads and validates a review's input without running the review. */
    public ReviewInput input(String index, String period) {
        var definition = catalog.index(index);
        var reviewPeriod = catalog.reviewPeriod(definition, period);
        return new ReviewInput(definition, reviewPeriod, catalog.loadInput(definition, reviewPeriod));
    }

    /** Runs a review and stores its report. */
    public ReviewRun run(String index, String period) {
        var input = input(index, period);
        var report = reportBuilder.build(engine.run(input.index(), input.period(), input.data()));
        return new ReviewRun(reportStore.save(report), report);
    }

    /** Stored runs of a review, oldest first. */
    public List<StoredReport> reports(String index, String period) {
        var definition = catalog.index(index);
        return reportStore.list(definition.name(), catalog.reviewPeriod(definition, period).id());
    }

    /** A stored report exactly as written, as JSON. */
    public byte[] report(String index, String period, String id) {
        var definition = catalog.index(index);
        return reportStore.read(definition.name(), catalog.reviewPeriod(definition, period).id(), id);
    }
}
