package com.example.indexreviewer.api;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What was loaded for a review, so the input can be checked before the review runs: the files read, how much
 * data each date has, the current composition, and the data-quality warnings.
 *
 * @param universeSizeOnReviewDate  securities in the universe on the review date
 * @param securityDataCountByDate   number of securities with market data, per date
 */
public record InputCheckResponse(
        String index,
        String reviewPeriod,
        LocalDate cutOffDate,
        LocalDate reviewDate,
        List<String> files,
        int universeSizeOnReviewDate,
        SortedMap<LocalDate, Long> securityDataCountByDate,
        List<String> currentComposition,
        List<DataQualityWarning> warnings) {

    static InputCheckResponse of(IndexDefinition index, ReviewPeriod period, InputData data) {
        var countByDate = new TreeMap<LocalDate, Long>();
        data.securityDataById().values().stream()
                .flatMap(byDate -> byDate.keySet().stream())
                .forEach(date -> countByDate.merge(date, 1L, Long::sum));
        return new InputCheckResponse(index.name(), period.id(), period.cutOffDate(), period.reviewDate(),
                data.files(), data.universe(period.reviewDate()).size(), countByDate,
                List.copyOf(data.currentComposition()), data.warnings());
    }
}
