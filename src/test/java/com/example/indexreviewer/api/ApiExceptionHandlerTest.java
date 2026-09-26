package com.example.indexreviewer.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void unexpectedErrorIsA500WithoutInternals() {
        var e = new UncheckedIOException("Cannot store report in /srv/reports/SMI", new IOException("disk full"));

        var problem = new ApiExceptionHandler().unexpected(e);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Internal error");
        assertThat(problem.getDetail()).doesNotContain("/srv/reports");
    }
}
