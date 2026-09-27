/**
 * Use cases of the application: list indices, check a review's input, run and store a review, read stored
 * reports. Looks up the configured indices ({@code IndexCatalog}) and chains {@code ingest}, {@code review},
 * {@code report} and {@code store}; the API only maps HTTP.
 */
package com.example.indexreviewer.service;
