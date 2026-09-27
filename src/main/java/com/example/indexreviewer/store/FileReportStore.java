package com.example.indexreviewer.store;

import com.example.indexreviewer.report.ReviewReport;
import com.example.indexreviewer.review.status.ReviewStatus;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Stores each report as a pretty-printed JSON file {@code <root>/<index>/<period>/<id>.json}. The id is the UTC
 * time the report was generated to the nanosecond, e.g. {@code 20260925T201052184253999Z}, so ids have a fixed
 * width and sort chronologically as text. Files are created, never overwritten: two runs in the same nanosecond
 * make the second one fail rather than replace the first.
 */
public final class FileReportStore implements ReportStore {

    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSSSSSSSS'Z'").withZone(ZoneOffset.UTC);
    /** Ids and path segments must not be able to leave the store's folder. */
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
    private static final String SUFFIX = ".json";

    private final Path root;
    private final JsonMapper mapper;
    /** Reads only the summary fields; a report without them fails instead of listing with gaps. */
    private final ObjectReader summaryReader;

    public FileReportStore(Path root, JsonMapper mapper) {
        this.root = root;
        this.mapper = mapper;
        this.summaryReader = mapper.readerFor(Summary.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
    }

    @Override
    public StoredReport save(ReviewReport report) {
        Path dir = folder(report.index(), report.reviewPeriod());
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        String id = ID_FORMAT.format(report.generatedAt());
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(id + SUFFIX), json, StandardOpenOption.CREATE_NEW);
            return new StoredReport(id, report.index(), report.reviewPeriod(), report.generatedAt(), report.status());
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
                    .sorted()
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
        Summary summary = summaryReader.readValue(read(index, reviewPeriod, id));
        return new StoredReport(id, index, reviewPeriod, summary.generatedAt(), summary.status());
    }

    /** The fields of a stored {@link ReviewReport} a listing shows; named like the report's components. */
    private record Summary(Instant generatedAt, ReviewStatus status) {
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
