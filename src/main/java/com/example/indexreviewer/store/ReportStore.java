package com.example.indexreviewer.store;

import com.example.indexreviewer.report.ReviewReport;

import java.util.List;

/**
 * Keeps every review run as written. Reports are never changed or deleted. Behind this interface a
 * database can replace the file store without touching the review.
 */
public interface ReportStore {

    /** Stores the report and returns its id, unique within its index and review period. */
    StoredReport save(ReviewReport report);

    /** Stored runs of a review period, oldest first. */
    List<StoredReport> list(String index, String reviewPeriod);

    /**
     * The stored report exactly as written, as JSON.
     *
     * @throws ReportNotFoundException if there is no such report
     */
    byte[] read(String index, String reviewPeriod, String id);
}
