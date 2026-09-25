package com.example.indexreviewer.api;

import com.example.indexreviewer.config.IndexCatalog;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/indices")
public class IndexController {

    private final IndexCatalog catalog;

    public IndexController(IndexCatalog catalog) {
        this.catalog = catalog;
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
}
