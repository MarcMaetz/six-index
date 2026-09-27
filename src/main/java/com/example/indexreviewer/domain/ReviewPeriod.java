package com.example.indexreviewer.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One scheduled review of an index, e.g. {@code 2026-Q3}.
 *
 * @param id          identifier, also the name of the period's input folder
 * @param cutOffDate  date t' whose prices are used
 * @param reviewDate  date t whose universe, shares and free float are used
 */
public record ReviewPeriod(String id, LocalDate cutOffDate, LocalDate reviewDate) {

    public ReviewPeriod {
        Validation.requireText(id, "review period id");
        Objects.requireNonNull(cutOffDate, "cut-off date of " + id);
        Objects.requireNonNull(reviewDate, "review date of " + id);
        Validation.require(!cutOffDate.isAfter(reviewDate),
                "review period %s: cut-off date %s is after review date %s".formatted(id, cutOffDate, reviewDate));
    }
}
