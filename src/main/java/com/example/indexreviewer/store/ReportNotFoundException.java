package com.example.indexreviewer.store;

/** No stored report with the requested id. */
public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(String message) {
        super(message);
    }
}
