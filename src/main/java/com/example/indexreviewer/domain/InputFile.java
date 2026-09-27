package com.example.indexreviewer.domain;

import java.util.Objects;

/**
 * An input file a review read, with the SHA-256 checksum of its content, so the report can prove which
 * input produced its result.
 *
 * @param path   path of the file as it was read
 * @param sha256 lower-case hex SHA-256 of the file's bytes
 */
public record InputFile(String path, String sha256) {

    public InputFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(sha256, "sha256");
    }
}
