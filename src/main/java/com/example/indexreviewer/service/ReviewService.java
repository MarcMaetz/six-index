package com.example.indexreviewer.service;

import com.example.indexreviewer.catalog.IndexCatalog;
import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;
import com.example.indexreviewer.ingest.InputSource;
import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.store.ReportStore;
import com.example.indexreviewer.store.StoredReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The application's use cases, addressed by index name and review period id. Any entry point (the REST API
 * today, a scheduler or CLI later) calls these instead of chaining catalog, input source, engine, report
 * builder and store itself.
 */
@Service
public class ReviewService {

    /** The input of a review, loaded and validated, with the index and period it was loaded for. */
    public record ReviewInput(IndexDefinition index, ReviewPeriod period, InputData data) {
    }

    /** A review that was run and stored. */
    public record ReviewRun(StoredReport stored, ReviewReport report) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(ReviewService.class);

    private final IndexCatalog catalog;
    private final InputSource inputSource;
    private final ReviewEngine engine;
    private final ReportBuilder reportBuilder;
    private final ReportStore reportStore;

    public ReviewService(IndexCatalog catalog, InputSource inputSource, ReviewEngine engine,
                         ReportBuilder reportBuilder, ReportStore reportStore) {
        this.catalog = catalog;
        this.inputSource = inputSource;
        this.engine = engine;
        this.reportBuilder = reportBuilder;
        this.reportStore = reportStore;
    }

    public List<IndexDefinition> indices() {
        return catalog.indices();
    }

    /** Loads and validates a review's input without running the review. */
    public ReviewInput input(String index, String period) {
        var review = review(index, period);
        var data = inputSource.load(review.index(), review.period());
        LOG.debug("Loaded input of {} {}: {} file(s), {} warning(s)", review.index().name(), review.period().id(),
                data.files().size(), data.warnings().size());
        return new ReviewInput(review.index(), review.period(), data);
    }

    /** Runs a review and stores its report. */
    public ReviewRun run(String index, String period) {
        var input = input(index, period);
        var report = reportBuilder.build(engine.run(input.index(), input.period(), input.data()));
        var stored = reportStore.save(report);
        LOG.info("Review {} {} stored as report {}: {}, {} warning(s)", stored.index(), stored.reviewPeriod(),
                stored.id(), stored.status(), report.warnings().size());
        return new ReviewRun(stored, report);
    }

    /** Stored runs of a review, oldest first. */
    public List<StoredReport> reports(String index, String period) {
        var review = review(index, period);
        return reportStore.list(review.index().name(), review.period().id());
    }

    /** A stored report exactly as written, as JSON. */
    public byte[] report(String index, String period, String id) {
        var review = review(index, period);
        return reportStore.read(review.index().name(), review.period().id(), id);
    }

    /** The configured index and review period; fails if either is not configured. */
    private Review review(String index, String period) {
        var definition = catalog.index(index);
        return new Review(definition, catalog.reviewPeriod(definition, period));
    }

    private record Review(IndexDefinition index, ReviewPeriod period) {
    }
}
