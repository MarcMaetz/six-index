package com.example.indexreviewer.ingest;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.DataQualityWarning.Impact;
import com.example.indexreviewer.domain.InputFile;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a {@code ;}-separated CSV file with a header row into rows addressable by column name. Handles a UTF-8
 * byte order mark and CRLF line endings. Blank lines are skipped; rows with the wrong number of fields are
 * reported as warnings and skipped. The checksum is taken from the same bytes that are parsed.
 */
final class CsvFile {

    private static final char SEPARATOR = ';';
    private static final int BYTE_ORDER_MARK = '﻿';

    /** One data row; {@code line} is its line number in the file (the header is line 1). */
    record Row(int line, Map<String, String> values) {

        /** The trimmed value of a column, empty string if blank. */
        String get(String column) {
            return values.get(column).trim();
        }
    }

    record Content(InputFile file, List<Row> rows, List<DataQualityWarning> warnings) {

        /** File name used as the source of warnings. */
        String source() {
            return Path.of(file.path()).getFileName().toString();
        }
    }

    private CsvFile() {
    }

    static Content read(Path file, List<String> requiredColumns) {
        if (!Files.isRegularFile(file)) {
            throw new InputDataException("Input file not found: " + file);
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            var inputFile = new InputFile(file.toString(), sha256(bytes));
            return parse(file.getFileName().toString(), inputFile, bytes, requiredColumns);
        } catch (IOException | CsvValidationException e) {
            throw new InputDataException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static Content parse(String source, InputFile inputFile, byte[] bytes, List<String> requiredColumns)
            throws IOException, CsvValidationException {
        try (var reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            skipByteOrderMark(reader);
            var csv = new CSVReaderBuilder(reader)
                    .withCSVParser(new CSVParserBuilder().withSeparator(SEPARATOR).build())
                    .build();
            List<String> columns = readHeader(csv, source, requiredColumns);
            var warnings = new ArrayList<DataQualityWarning>();
            var rows = readRows(csv, source, columns, warnings);
            return new Content(inputFile, rows, warnings);
        }
    }

    /** Data rows after the header; blank lines are skipped, rows with the wrong field count are warned about. */
    private static List<Row> readRows(CSVReader csv, String source, List<String> columns,
                                      List<DataQualityWarning> warnings) throws IOException, CsvValidationException {
        var rows = new ArrayList<Row>();
        String[] fields;
        while ((fields = csv.readNext()) != null) {
            int line = (int) csv.getLinesRead();
            if (fields.length == 1 && fields[0].isBlank()) {
                continue;
            }
            if (fields.length != columns.size()) {
                warnings.add(new DataQualityWarning(source, line, Impact.MISSING_DATA,
                        "Row ignored: expected %d fields, found %d".formatted(columns.size(), fields.length),
                        List.of()));
                continue;
            }
            var values = new HashMap<String, String>();
            for (int i = 0; i < columns.size(); i++) {
                values.put(columns.get(i), fields[i]);
            }
            rows.add(new Row(line, values));
        }
        return rows;
    }

    /** The trimmed column names; fails if the file is empty or a required column is missing. */
    private static List<String> readHeader(CSVReader csv, String source, List<String> requiredColumns)
            throws IOException, CsvValidationException {
        String[] header = csv.readNext();
        if (header == null) {
            throw new InputDataException(source + " is empty, expected header " + requiredColumns);
        }
        List<String> columns = List.of(header).stream().map(String::trim).toList();
        List<String> missing = requiredColumns.stream().filter(c -> !columns.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new InputDataException(source + " is missing column(s) " + missing + ", found " + columns);
        }
        return columns;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    private static void skipByteOrderMark(BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
    }
}
