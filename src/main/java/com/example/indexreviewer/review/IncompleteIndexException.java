package com.example.indexreviewer.review;

/**
 * Fewer securities could be ranked than the index has constituents, so no valid composition exists (A12). The
 * review stops instead of publishing a smaller index.
 */
public class IncompleteIndexException extends RuntimeException {

    public IncompleteIndexException(String message) {
        super(message);
    }
}
