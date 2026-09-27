package com.example.indexreviewer.service;

/** A requested index or review period is not in the configuration. */
public class NotConfiguredException extends RuntimeException {

    public NotConfiguredException(String message) {
        super(message);
    }
}
