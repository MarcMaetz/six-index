package com.example.indexreviewer.api;

import com.example.indexreviewer.ingest.InputDataException;
import com.example.indexreviewer.review.IncompleteIndexException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void unusableInputIsA422WithTheReason() {
        var problem = new ApiExceptionHandler().unusableInput(
                new InputDataException("sec_data.csv is missing column(s) [free_float], found [id, date]"));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Unusable input data");
        assertThat(problem.getDetail()).isEqualTo("sec_data.csv is missing column(s) [free_float], found [id, date]");
    }

    @Test
    void tooFewEligibleSecuritiesIsA422WithTheReason() {
        var problem = new ApiExceptionHandler().incompleteIndex(
                new IncompleteIndexException("SMI needs 20 constituents, but only 4 securities could be ranked"));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Too few eligible securities");
        assertThat(problem.getDetail()).isEqualTo("SMI needs 20 constituents, but only 4 securities could be ranked");
    }

    @Test
    void unexpectedErrorIsA500WithoutInternals() {
        var e = new UncheckedIOException("Cannot store report in /srv/reports/SMI", new IOException("disk full"));

        var problem = new ApiExceptionHandler().unexpected(e);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Internal error");
        assertThat(problem.getDetail()).doesNotContain("/srv/reports");
    }
}
