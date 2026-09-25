package com.example.indexreviewer.ingest;

/**
 * Input that cannot be used at all, such as a missing file or a missing column. Problems with single rows are
 * reported as data-quality warnings instead.
 */
public class InputDataException extends RuntimeException {

    public InputDataException(String message) {
        super(message);
    }

    public InputDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
