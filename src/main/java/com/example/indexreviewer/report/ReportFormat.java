package com.example.indexreviewer.report;

/**
 * Display precision of the report. Calculations run at full precision; only the report rounds.
 *
 * @param weightDecimals        decimals of weights, in percent
 * @param cappingFactorDecimals decimals of capping factors
 * @param ffmcapDecimals        decimals of FFMCAP values
 */
public record ReportFormat(int weightDecimals, int cappingFactorDecimals, int ffmcapDecimals) {

    public ReportFormat {
        if (weightDecimals < 0 || cappingFactorDecimals < 0 || ffmcapDecimals < 0) {
            throw new IllegalArgumentException("Report decimals must not be negative");
        }
    }
}
