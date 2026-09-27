/**
 * Spring wiring, the outermost layer: binds the configuration ({@code config/indices.yml} and
 * {@code application.properties}) and makes the framework-free catalog, input source, engine, report builder and
 * report store available as beans. Nothing else in the application depends on it.
 */
package com.example.indexreviewer.config;
