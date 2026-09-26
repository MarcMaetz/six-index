package com.example.indexreviewer.api;

import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.service.ReviewService;
import com.example.indexreviewer.store.StoredReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Maps HTTP to the {@link ReviewService} use cases (D24); holds no review logic of its own. */
@RestController
@RequestMapping("/api/indices")
public class IndexController {

    private final ReviewService service;

    public IndexController(ReviewService service) {
        this.service = service;
    }

    @Operation(summary = "List the configured indices and their review periods")
    @GetMapping
    public List<IndexResponse> indices() {
        return service.indices().stream().map(IndexResponse::of).toList();
    }

    @Operation(summary = "Load and validate the input of a review without running it",
            description = "Returns the files read (with SHA-256 checksums), data counts, the current composition "
                    + "and all data-quality warnings.")
    @GetMapping("/{index}/reviews/{period}/input")
    public InputCheckResponse input(@PathVariable String index, @PathVariable String period) {
        var input = service.input(index, period);
        return InputCheckResponse.of(input.index(), input.period(), input.data());
    }

    @Operation(summary = "Run a review, store its report and return it",
            description = "Loads the review's input, ranks, selects and caps, and returns the new composition "
                    + "with weights, joiners, leavers, review status and the audit trail. Every run is stored "
                    + "as written; the Location header points to the stored report.")
    @PostMapping("/{index}/reviews/{period}")
    public ResponseEntity<ReviewReport> review(@PathVariable String index, @PathVariable String period) {
        var run = service.run(index, period);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/reports/{id}").buildAndExpand(run.stored().id()).toUri();
        return ResponseEntity.created(location).body(run.report());
    }

    @Operation(summary = "List the stored runs of a review, oldest first")
    @GetMapping("/{index}/reviews/{period}/reports")
    public List<StoredReport> reports(@PathVariable String index, @PathVariable String period) {
        return service.reports(index, period);
    }

    /** Returned as stored bytes (D21); the annotation documents their structure, the current report schema (D34). */
    @Operation(summary = "Get a stored report exactly as it was written")
    @ApiResponse(responseCode = "200", description = "The stored report",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ReviewReport.class)))
    @GetMapping(value = "/{index}/reviews/{period}/reports/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] report(@PathVariable String index, @PathVariable String period, @PathVariable String id) {
        return service.report(index, period, id);
    }
}
