package com.example.indexreviewer.domain;

import java.util.List;
import java.util.Objects;

/**
 * A data-quality issue found in the input that did not stop the review, e.g. a duplicate or invalid row that
 * was ignored. Warnings are carried into the review report for traceability.
 *
 * @param source      input the issue was found in, e.g. a file name
 * @param line        line number in the source, or {@code null} if the warning covers the whole source
 * @param message     what was found and what was done about it
 * @param securityIds securities affected, empty if none can be named
 */
public record DataQualityWarning(String source, Integer line, String message, List<String> securityIds) {

    public DataQualityWarning {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(message, "message");
        securityIds = List.copyOf(securityIds);
    }

    @Override
    public String toString() {
        return source + (line == null ? "" : ":" + line) + ": " + message;
    }
}
