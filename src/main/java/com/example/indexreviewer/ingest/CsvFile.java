package com.example.indexreviewer.ingest;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a {@code ;}-separated CSV file with a header row into rows addressable by column name. Handles a UTF-8
 * byte order mark and CRLF line endings. Blank lines are skipped; rows with the wrong number of fields are
 * reported as warnings and skipped.
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

    record Content(List<Row> rows, List<DataQualityWarning> warnings) {
    }

    private CsvFile() {
    }

    static Content read(Path file, List<String> requiredColumns) {
        if (!Files.isRegularFile(file)) {
            throw new InputDataException("Input file not found: " + file);
        }
        String source = file.getFileName().toString();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            skipByteOrderMark(reader);
            var csv = new CSVReaderBuilder(reader)
                    .withCSVParser(new CSVParserBuilder().withSeparator(SEPARATOR).build())
                    .build();

            String[] header = csv.readNext();
            if (header == null) {
                throw new InputDataException(source + " is empty, expected header " + requiredColumns);
            }
            List<String> columns = List.of(header).stream().map(String::trim).toList();
            List<String> missing = requiredColumns.stream().filter(c -> !columns.contains(c)).toList();
            if (!missing.isEmpty()) {
                throw new InputDataException(source + " is missing column(s) " + missing + ", found " + columns);
            }

            var rows = new ArrayList<Row>();
            var warnings = new ArrayList<DataQualityWarning>();
            String[] fields;
            while ((fields = csv.readNext()) != null) {
                int line = (int) csv.getLinesRead();
                if (fields.length == 1 && fields[0].isBlank()) {
                    continue;
                }
                if (fields.length != columns.size()) {
                    warnings.add(new DataQualityWarning(source, line, "Row ignored: expected %d fields, found %d"
                            .formatted(columns.size(), fields.length), List.of()));
                    continue;
                }
                var values = new HashMap<String, String>();
                for (int i = 0; i < columns.size(); i++) {
                    values.put(columns.get(i), fields[i]);
                }
                rows.add(new Row(line, values));
            }
            return new Content(rows, warnings);
        } catch (IOException | CsvValidationException e) {
            throw new InputDataException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static void skipByteOrderMark(BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
    }
}
