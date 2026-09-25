package com.example.indexreviewer.api;

import com.example.indexreviewer.config.NotConfiguredException;
import com.example.indexreviewer.ingest.InputDataException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain errors to RFC 9457 problem responses. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler
    ProblemDetail notConfigured(NotConfiguredException e) {
        return problem(HttpStatus.NOT_FOUND, "Not configured", e);
    }

    /** The request is valid, but the review's input files are missing or unusable. */
    @ExceptionHandler
    ProblemDetail unusableInput(InputDataException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Unusable input data", e);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException e) {
        var problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
