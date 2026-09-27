package com.example.indexreviewer.domain;

import java.util.List;
import java.util.Objects;

/**
 * A data-quality issue found in the input that did not stop the review, e.g. a duplicate or invalid row that
 * was ignored. Warnings are carried into the review report for traceability.
 *
 * @param source      input the issue was found in, e.g. a file name
 * @param line        line number in the source, or {@code null} if the warning covers the whole source
 * @param impact      whether data the review could need was lost
 * @param message     what was found and what was done about it
 * @param securityIds securities affected, empty if none can be named
 */
public record DataQualityWarning(String source, Integer line, Impact impact, String message,
                                 List<String> securityIds) {

    /** Whether a warning can affect the review result; drives the review status. */
    public enum Impact {
        /** Nothing was lost, e.g. an identical duplicate row was dropped. */
        NONE,
        /** Data could not be used, e.g. an invalid row was ignored or a security could not be ranked. */
        MISSING_DATA
    }

    public DataQualityWarning {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(impact, "impact");
        Objects.requireNonNull(message, "message");
        securityIds = List.copyOf(securityIds);
    }

    @Override
    public String toString() {
        return source + (line == null ? "" : ":" + line) + ": " + message;
    }
}
