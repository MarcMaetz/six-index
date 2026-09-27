package com.example.indexreviewer.store;

import com.example.indexreviewer.review.status.ReviewStatus;

import java.time.Instant;

/** A stored review run: its id and the fields needed to pick one from a list. */
public record StoredReport(String id, String index, String reviewPeriod, Instant generatedAt, ReviewStatus status) {
}
