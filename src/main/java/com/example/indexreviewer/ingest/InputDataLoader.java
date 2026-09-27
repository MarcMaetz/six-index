package com.example.indexreviewer.ingest;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;
import com.example.indexreviewer.domain.InputData;
import com.example.indexreviewer.domain.SecurityData;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Loads and validates the input of one review from its folder ({@code <data-dir>/<index>/<period>}), which
 * holds {@code <universe>_universe.csv} (e.g. {@code spi_universe.csv}), {@code sec_data.csv} and
 * {@code composition.csv}.
 * <p>
 * Only unusable input (missing file or column) fails the load. Invalid and duplicate rows are skipped and
 * reported as {@link DataQualityWarning}s, so a single bad row does not block a review but stays visible in
 * the report. Checks that depend on the review dates (e.g. a security without review-date data, A2) belong to
 * the review, not here.
 */
public final class InputDataLoader {

    static final String UNIVERSE_FILE_SUFFIX = "_universe.csv";
    static final String SECURITY_DATA_FILE = "sec_data.csv";
    static final String COMPOSITION_FILE = "composition.csv";

    private static final String ID = "id";
    private static final String DATE = "date";
    private static final String PRICE = "price";
    private static final String FREE_FLOAT = "free_float";
    private static final String SHARES = "shares";

    /** File name of a universe's constituent list, e.g. {@code SPI} → {@code spi_universe.csv}. */
    static String universeFileName(String universe) {
        return universe.toLowerCase(Locale.ROOT) + UNIVERSE_FILE_SUFFIX;
    }

    /**
     * Loads one review's input, keeping every warning raised on the way.
     *
     * @param inputDir folder of one review's input files
     * @param universe universe of the index, which names the universe file
     */
    public InputData load(Path inputDir, String universe) {
        if (!Files.isDirectory(inputDir)) {
            throw new InputDataException("Input folder not found: " + inputDir);
        }
        var universeFile = new FileLoad(inputDir.resolve(universeFileName(universe)), List.of(DATE, ID));
        var universeByDate = loadUniverse(universeFile);
        var securityFile = new FileLoad(inputDir.resolve(SECURITY_DATA_FILE),
                List.of(ID, DATE, PRICE, FREE_FLOAT, SHARES));
        var securityData = loadSecurityData(securityFile);
        var compositionFile = new FileLoad(inputDir.resolve(COMPOSITION_FILE), List.of(ID));
        var composition = loadComposition(compositionFile);

        var loaded = List.of(universeFile, securityFile, compositionFile);
        return new InputData(universeByDate, securityData, composition,
                loaded.stream().flatMap(file -> file.warnings.stream()).toList(),
                loaded.stream().map(file -> file.content.file().toString()).toList());
    }

    /** A1: exact duplicate rows are dropped with one summary warning. */
    private Map<LocalDate, Set<String>> loadUniverse(FileLoad file) {
        var universe = new LinkedHashMap<LocalDate, Set<String>>();
        var duplicates = new ArrayList<String>();
        for (var parsed : file.parseRows(row -> new UniverseMember(parseDate(row, DATE), requireValue(row, ID)))) {
            var member = parsed.value();
            if (!universe.computeIfAbsent(member.date(), d -> new LinkedHashSet<>()).add(member.id())) {
                duplicates.add(member.id());
            }
        }
        file.warnDuplicates(duplicates);
        return universe;
    }

    /**
     * Rows with values out of range are skipped. Identical duplicates are dropped with one summary warning;
     * conflicting rows for the same security and date are all dropped (A9), since neither can be trusted.
     */
    private Map<String, Map<LocalDate, SecurityData>> loadSecurityData(FileLoad file) {
        var byId = new LinkedHashMap<String, Map<LocalDate, SecurityData>>();
        var duplicates = new ArrayList<String>();
        var conflicts = new LinkedHashSet<SecurityData>();
        for (var parsed : file.parseRows(row -> new SecurityData(requireValue(row, ID), parseDate(row, DATE),
                parsePrice(row), parseFreeFloat(row), parseShares(row)))) {
            var data = parsed.value();
            var byDate = byId.computeIfAbsent(data.securityId(), id -> new LinkedHashMap<>());
            var existing = byDate.putIfAbsent(data.date(), data);
            if (existing == null) {
                continue;
            }
            if (existing.sameValuesAs(data)) {
                duplicates.add(data.securityId());
            } else {
                conflicts.add(existing);
                file.warn(parsed.line(), Impact.MISSING_DATA,
                        "Conflicting data for %s on %s; all rows for that date ignored"
                                .formatted(data.securityId(), data.date()), List.of(data.securityId()));
            }
        }
        for (var conflict : conflicts) {
            byId.get(conflict.securityId()).remove(conflict.date());
        }
        byId.values().removeIf(Map::isEmpty);
        file.warnDuplicates(duplicates);
        return byId;
    }

    private Set<String> loadComposition(FileLoad file) {
        var composition = new LinkedHashSet<String>();
        var duplicates = new ArrayList<String>();
        for (var parsed : file.parseRows(row -> requireValue(row, ID))) {
            if (!composition.add(parsed.value())) {
                duplicates.add(parsed.value());
            }
        }
        file.warnDuplicates(duplicates);
        return composition;
    }

    private static String requireValue(CsvFile.Row row, String column) {
        String value = row.get(column);
        if (value.isEmpty()) {
            throw new InvalidRowException(column + " is empty");
        }
        return value;
    }

    private static LocalDate parseDate(CsvFile.Row row, String column) {
        String value = requireValue(row, column);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidRowException(column + " '" + value + "' is not an ISO date");
        }
    }

    private static BigDecimal parsePrice(CsvFile.Row row) {
        BigDecimal price = parseOptionalDecimal(row, PRICE);
        if (price != null && price.signum() <= 0) {
            throw new InvalidRowException("price " + price + " is not positive");
        }
        return price;
    }

    private static BigDecimal parseFreeFloat(CsvFile.Row row) {
        BigDecimal freeFloat = parseOptionalDecimal(row, FREE_FLOAT);
        if (freeFloat != null && (freeFloat.signum() <= 0 || freeFloat.compareTo(BigDecimal.ONE) > 0)) {
            throw new InvalidRowException("free_float " + freeFloat + " is not in (0, 1]");
        }
        return freeFloat;
    }

    private static Long parseShares(CsvFile.Row row) {
        BigDecimal shares = parseOptionalDecimal(row, SHARES);
        if (shares == null) {
            return null;
        }
        if (shares.signum() <= 0 || shares.stripTrailingZeros().scale() > 0) {
            throw new InvalidRowException("shares " + shares + " is not a positive whole number");
        }
        try {
            return shares.longValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidRowException("shares " + shares + " is out of range");
        }
    }

    private static BigDecimal parseOptionalDecimal(CsvFile.Row row, String column) {
        String value = row.get(column);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidRowException(column + " '" + value + "' is not a number");
        }
    }

    /**
     * One input file while it is loaded: its rows, and every warning about it in the order they arise, starting
     * with those from reading the CSV.
     */
    private static final class FileLoad {

        private final CsvFile.Content content;
        private final List<DataQualityWarning> warnings;

        FileLoad(Path file, List<String> columns) {
            this.content = CsvFile.read(file, columns);
            this.warnings = new ArrayList<>(content.warnings());
        }

        /**
         * Parses each row; a row the parser rejects is skipped with a warning, so the load loops only see valid
         * values. Invalid-row warnings therefore come before the warnings the loader adds while processing.
         */
        <T> List<ParsedRow<T>> parseRows(Function<CsvFile.Row, T> parser) {
            var parsed = new ArrayList<ParsedRow<T>>();
            for (var row : content.rows()) {
                try {
                    parsed.add(new ParsedRow<>(row.line(), parser.apply(row)));
                } catch (InvalidRowException e) {
                    String id = row.get(ID);
                    warn(row.line(), Impact.MISSING_DATA, "Row ignored: " + e.getMessage(),
                            id.isEmpty() ? List.of() : List.of(id));
                }
            }
            return parsed;
        }

        void warnDuplicates(List<String> duplicates) {
            if (!duplicates.isEmpty()) {
                warn(null, Impact.NONE, "%d duplicate row(s) ignored".formatted(duplicates.size()), duplicates);
            }
        }

        void warn(Integer line, Impact impact, String message, List<String> securityIds) {
            warnings.add(new DataQualityWarning(content.source(), line, impact, message, securityIds));
        }
    }

    /** A row's parsed value with its line number, for warnings raised after parsing. */
    private record ParsedRow<T>(int line, T value) {
    }

    private record UniverseMember(LocalDate date, String id) {
    }

    private static final class InvalidRowException extends RuntimeException {
        InvalidRowException(String message) {
            super(message);
        }
    }
}
