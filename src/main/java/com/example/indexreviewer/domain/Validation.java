package com.example.indexreviewer.domain;

/** Argument checks for the domain records. */
final class Validation {

    private Validation() {
    }

    static void require(boolean condition, String messageFormat, Object... args) {
        if (!condition) {
            throw new IllegalArgumentException(messageFormat.formatted(args));
        }
    }

    static void requireText(String value, String what) {
        require(value != null && !value.isBlank(), "%s must not be blank", what);
    }
}
