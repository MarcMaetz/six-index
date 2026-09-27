package com.example.indexreviewer.domain;

/** Argument checks for the domain records. */
final class Validation {

    private Validation() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static void requireText(String value, String what) {
        require(value != null && !value.isBlank(), what + " must not be blank");
    }
}
