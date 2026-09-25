package com.example.indexreviewer.store;

import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.report.ReviewStatus;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Stores each report as a pretty-printed JSON file {@code <root>/<index>/<period>/<id>.json}. The id is the UTC
 * time the report was generated, e.g. {@code 20260925T201052184Z}, with a {@code -2}, {@code -3}, … suffix if
 * two runs share a millisecond. Files are created, never overwritten.
 */
public final class FileReportStore implements ReportStore {

    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    /** Ids and path segments must not be able to leave the store's folder. */
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
    private static final String SUFFIX = ".json";
    /** Chronological: by time, then by suffix as a number, so "…Z" < "…Z-2" < "…Z-10". */
    private static final Comparator<String> RUN_ORDER = Comparator
            .comparing((String id) -> id.split("-")[0])
            .thenComparingInt(id -> id.contains("-") ? Integer.parseInt(id.substring(id.indexOf('-') + 1)) : 1);

    private final Path root;
    private final JsonMapper mapper;

    public FileReportStore(Path root, JsonMapper mapper) {
        this.root = root;
        this.mapper = mapper;
    }

    @Override
    public StoredReport save(ReviewReport report) {
        Path dir = folder(report.index(), report.reviewPeriod());
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        String baseId = ID_FORMAT.format(report.generatedAt());
        try {
            Files.createDirectories(dir);
            for (int attempt = 1; ; attempt++) {
                String id = attempt == 1 ? baseId : baseId + "-" + attempt;
                try {
                    Files.write(dir.resolve(id + SUFFIX), json, StandardOpenOption.CREATE_NEW);
                    return new StoredReport(id, report.index(), report.reviewPeriod(), report.generatedAt(),
                            report.status());
                } catch (FileAlreadyExistsException e) {
                    // Another run in the same millisecond: try the next suffix.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store report in " + dir, e);
        }
    }

    @Override
    public List<StoredReport> list(String index, String reviewPeriod) {
        Path dir = folder(index, reviewPeriod);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(SUFFIX))
                    .map(name -> name.substring(0, name.length() - SUFFIX.length()))
                    .sorted(RUN_ORDER)
                    .map(id -> summary(index, reviewPeriod, id))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list reports in " + dir, e);
        }
    }

    @Override
    public byte[] read(String index, String reviewPeriod, String id) {
        if (!SAFE_SEGMENT.matcher(id).matches()) {
            throw notFound(index, reviewPeriod, id);
        }
        Path file = folder(index, reviewPeriod).resolve(id + SUFFIX);
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw notFound(index, reviewPeriod, id);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read report " + file, e);
        }
    }

    private StoredReport summary(String index, String reviewPeriod, String id) {
        var json = mapper.readTree(read(index, reviewPeriod, id));
        return new StoredReport(id, index, reviewPeriod, Instant.parse(json.get("generatedAt").asString()),
                ReviewStatus.valueOf(json.get("status").asString()));
    }

    private Path folder(String index, String reviewPeriod) {
        for (String segment : List.of(index, reviewPeriod)) {
            if (!SAFE_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("Unsafe path segment: " + segment);
            }
        }
        return root.resolve(index).resolve(reviewPeriod);
    }

    private static ReportNotFoundException notFound(String index, String reviewPeriod, String id) {
        return new ReportNotFoundException("No stored report " + id + " for " + index + " " + reviewPeriod);
    }
}
