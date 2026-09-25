package com.example.indexreviewer.api;

import com.example.indexreviewer.config.IndexCatalog;
import com.example.indexreviewer.report.ReportBuilder;
import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.ReviewEngine;
import com.example.indexreviewer.store.ReportStore;
import com.example.indexreviewer.store.StoredReport;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/indices")
public class IndexController {

    private final IndexCatalog catalog;
    private final ReviewEngine engine;
    private final ReportBuilder reportBuilder;
    private final ReportStore reportStore;

    public IndexController(IndexCatalog catalog, ReviewEngine engine, ReportBuilder reportBuilder,
                           ReportStore reportStore) {
        this.catalog = catalog;
        this.engine = engine;
        this.reportBuilder = reportBuilder;
        this.reportStore = reportStore;
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

    @Operation(summary = "Run a review, store its report and return it",
            description = "Loads the review's input, ranks, selects and caps, and returns the new composition "
                    + "with weights, joiners, leavers, review status and the audit trail. Every run is stored "
                    + "as written; the Location header points to the stored report.")
    @PostMapping("/{index}/reviews/{period}")
    public ResponseEntity<ReviewReport> review(@PathVariable String index, @PathVariable String period) {
        var definition = catalog.index(index);
        var reviewPeriod = catalog.reviewPeriod(definition, period);
        var result = engine.run(definition, reviewPeriod, catalog.loadInput(definition, reviewPeriod));
        var report = reportBuilder.build(result);
        var stored = reportStore.save(report);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/reports/{id}").buildAndExpand(stored.id()).toUri();
        return ResponseEntity.created(location).body(report);
    }

    @Operation(summary = "List the stored runs of a review, oldest first")
    @GetMapping("/{index}/reviews/{period}/reports")
    public List<StoredReport> reports(@PathVariable String index, @PathVariable String period) {
        var definition = catalog.index(index);
        var reviewPeriod = catalog.reviewPeriod(definition, period);
        return reportStore.list(definition.name(), reviewPeriod.id());
    }

    @Operation(summary = "Get a stored report exactly as it was written")
    @GetMapping(value = "/{index}/reviews/{period}/reports/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] report(@PathVariable String index, @PathVariable String period, @PathVariable String id) {
        var definition = catalog.index(index);
        var reviewPeriod = catalog.reviewPeriod(definition, period);
        return reportStore.read(definition.name(), reviewPeriod.id(), id);
    }
}
