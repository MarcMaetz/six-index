package com.example.indexreviewer.api;

import com.example.indexreviewer.ingest.InputDataException;
import com.example.indexreviewer.review.IncompleteIndexException;
import com.example.indexreviewer.service.NotConfiguredException;
import com.example.indexreviewer.store.ReportNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 9457 problem response: domain errors here, Spring MVC's own (unknown path,
 * wrong method, …) through {@link ResponseEntityExceptionHandler}, and anything unexpected to a 500.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler
    ProblemDetail notConfigured(NotConfiguredException e) {
        return problem(HttpStatus.NOT_FOUND, "Not configured", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail reportNotFound(ReportNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Report not found", e.getMessage());
    }

    /** The request is valid, but the review's input files are missing or unusable. */
    @ExceptionHandler
    ProblemDetail unusableInput(InputDataException e) {
        LOG.warn("Unusable input data: {}", e.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Unusable input data", e.getMessage());
    }

    /** The input is readable, but too few securities could be ranked to build the index (A12). */
    @ExceptionHandler
    ProblemDetail incompleteIndex(IncompleteIndexException e) {
        LOG.warn("Review stopped: {}", e.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Too few eligible securities", e.getMessage());
    }

    /** A bug or an I/O failure: logged with its stack trace, but no internals (paths, messages) in the response. */
    @ExceptionHandler
    ProblemDetail unexpected(Exception e) {
        LOG.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "Unexpected error, see the server log");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
