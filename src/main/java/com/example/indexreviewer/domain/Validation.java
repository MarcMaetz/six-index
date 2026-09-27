package com.example.indexreviewer.domain;

import java.util.regex.Pattern;

/** Argument checks for the domain records. */
final class Validation {

    /** Letters, digits, '_', '.' and '-', starting with a letter or digit: safe as a folder name, no "..". */
    private static final Pattern FOLDER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");

    private Validation() {}

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static void requireText(String value, String what) {
        require(value != null && !value.isBlank(), what + " must not be blank");
    }

    /** For names that become folders of the input data and the stored reports. */
    static void requireFolderName(String value, String what) {
        requireText(value, what);
        require(
                FOLDER_NAME.matcher(value).matches(),
                ("%s '%s' is used as a folder name: only letters, digits, "
                                + "'_', '.' and '-', starting with a letter or digit")
                        .formatted(what, value));
    }
}
