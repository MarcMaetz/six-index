package com.example.indexreviewer.review;

/** A universe security that cannot be ranked, and why. */
public record Exclusion(String securityId, String reason) {
}
