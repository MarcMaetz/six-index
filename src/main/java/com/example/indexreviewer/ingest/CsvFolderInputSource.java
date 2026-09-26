package com.example.indexreviewer.ingest;

import com.example.indexreviewer.domain.IndexDefinition;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.ReviewPeriod;

import java.nio.file.Path;

/** Reads a review's CSVs from {@code <data-dir>/<index>/<period>}, e.g. {@code data/SMI/2026-Q3} (D12). */
public final class CsvFolderInputSource implements InputSource {

    private final Path dataDir;
    private final InputDataLoader loader = new InputDataLoader();

    public CsvFolderInputSource(Path dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public InputData load(IndexDefinition index, ReviewPeriod period) {
        return loader.load(dataDir.resolve(index.name()).resolve(period.id()).normalize(), index.universe());
    }
}
