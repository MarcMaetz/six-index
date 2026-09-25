package com.example.indexreviewer.api;

import com.example.indexreviewer.config.IndexCatalog;
import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.ReviewEngine;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/indices")
public class IndexController {

    private final IndexCatalog catalog;
    private final ReviewEngine engine;
    private final ReportBuilder reportBuilder;

    public IndexController(IndexCatalog catalog, ReviewEngine engine, ReportBuilder reportBuilder) {
        this.catalog = catalog;
        this.engine = engine;
        this.reportBuilder = reportBuilder;
    }

    @Operation(summary = "List the configured indices and their review periods")
    @GetMapping
    public List<IndexResponse> indices() {
        return catalog.indices().stream().map(IndexResponse::of).toList();
    }

    @Operation(summary = "Load and validate the input of a review without running it",
            description = "Returns the files read (with SHA-256 checksums), data counts, the current composition "
                    + "and all data-quality warnings.")
    @GetMapping("/{index}/reviews/{period}/input")
    public InputCheckResponse input(@PathVariable String index, @PathVariable String period) {
        var definition = catalog.index(index);
        var reviewPeriod = catalog.reviewPeriod(definition, period);
        return InputCheckResponse.of(definition, reviewPeriod, catalog.loadInput(definition, reviewPeriod));
    }

    @Operation(summary = "Run a review and return its report",
            description = "Loads the review's input, ranks, selects and caps, and returns the new composition "
                    + "with weights, joiners, leavers, review status and the audit trail. Nothing is stored; "
                    + "the same input always gives the same report apart from generatedAt.")
    @PostMapping("/{index}/reviews/{period}")
    public ReviewReport review(@PathVariable String index, @PathVariable String period) {
        var definition = catalog.index(index);
        var reviewPeriod = catalog.reviewPeriod(definition, period);
        var result = engine.run(definition, reviewPeriod, catalog.loadInput(definition, reviewPeriod));
        return reportBuilder.build(result);
    }
}
