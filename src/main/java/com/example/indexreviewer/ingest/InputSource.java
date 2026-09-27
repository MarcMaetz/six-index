package com.example.indexreviewer.ingest;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;

/**
 * Where a review's input comes from. {@link CsvFolderInputSource} reads the CSV folder of the review; a
 * market-data system or database could replace it without touching the review.
 */
public interface InputSource {

    /**
     * Loads and validates the input of one review. Problem rows are skipped and reported as warnings.
     *
     * @throws InputDataException if the input is missing or unusable
     */
    InputData load(IndexDefinition index, ReviewPeriod period);
}
