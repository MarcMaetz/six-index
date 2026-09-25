package com.example.indexreviewer.ingest;

import com.example.indexreviewer.domain.DataQualityWarning;
import com.example.indexreviewer.domain.InputData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class InputDataLoaderTest {

    private static final LocalDate CUT_OFF = LocalDate.parse("2026-09-10");
    private static final LocalDate REVIEW = LocalDate.parse("2026-09-21");

    private final InputDataLoader loader = new InputDataLoader();

    @TempDir
    Path dir;

    @Test
    void loadsProvidedData() {
        InputData data = loader.load(Path.of("data"));

        assertThat(data.universe(REVIEW)).hasSize(205);
        assertThat(data.currentComposition()).hasSize(20);
        assertThat(data.securityData("155", CUT_OFF)).get()
                .satisfies(d -> assertThat(d.price()).isNotNull());
        assertThat(data.securityData("155", REVIEW)).get()
                .satisfies(d -> assertThat(d.price()).isNull());
        assertThat(data.securityData("166", CUT_OFF)).isPresent();
        assertThat(data.securityData("166", REVIEW)).isEmpty();

        // A1: 204 ids appear twice in the universe; that is the only issue in the provided data.
        assertThat(data.warnings()).singleElement().satisfies(w -> {
            assertThat(w.source()).isEqualTo("spi_universe.csv");
            assertThat(w.securityIds()).hasSize(204).doesNotContain("166");
        });
    }

    @Test
    void handlesByteOrderMarkAndCrlf() throws IOException {
        writeFiles(
                "date;id\r\n2026-09-21;1\r\n",
                "id;date;price;free_float;shares\r\n1;2026-09-10;12.5;0.8;1000\r\n1;2026-09-21;;0.9;1100\r\n",
                "id\r\n1\r\n");

        InputData data = loader.load(dir);

        assertThat(data.universe(REVIEW)).containsExactly("1");
        assertThat(data.currentComposition()).containsExactly("1");
        assertThat(data.securityData("1", CUT_OFF)).get().satisfies(d -> {
            assertThat(d.price()).isEqualByComparingTo("12.5");
            assertThat(d.freeFloat()).isEqualByComparingTo("0.8");
            assertThat(d.shares()).isEqualTo(1000L);
        });
        assertThat(data.securityData("1", REVIEW)).get().satisfies(d -> assertThat(d.price()).isNull());
        assertThat(data.warnings()).isEmpty();
    }

    @Test
    void dropsDuplicateUniverseAndCompositionRowsWithWarning() throws IOException {
        writeFiles(
                "date;id\n2026-09-21;1\n2026-09-21;2\n2026-09-21;1\n",
                "id;date;price;free_float;shares\n",
                "id\n1\n1\n");

        InputData data = loader.load(dir);

        assertThat(data.universe(REVIEW)).containsExactly("1", "2");
        assertThat(data.currentComposition()).containsExactly("1");
        assertThat(data.warnings()).extracting(DataQualityWarning::source, DataQualityWarning::securityIds)
                .containsExactly(
                        tuple("spi_universe.csv", List.of("1")),
                        tuple("composition.csv", List.of("1")));
    }

    @Test
    void skipsInvalidRowsWithLineNumbers() throws IOException {
        writeFiles(
                "date;id\n2026-09-21;1\n21.09.2026;2\n2026-09-21;\n",
                """
                        id;date;price;free_float;shares
                        1;2026-09-10;abc;0.5;100
                        2;2026-09-10;10;1.5;100
                        3;2026-09-10;-1;0.5;100
                        4;2026-09-10;10;0.5;10.5
                        5;2026-09-10;10;0.5
                        6;2026-09-10;10;1;100
                        """,
                "id\n1\n");

        InputData data = loader.load(dir);

        assertThat(data.universe(REVIEW)).containsExactly("1");
        assertThat(data.securityDataById()).containsOnlyKeys("6");
        assertThat(data.warnings()).extracting(DataQualityWarning::toString).containsExactly(
                "spi_universe.csv:3: Row ignored: date '21.09.2026' is not an ISO date",
                "spi_universe.csv:4: Row ignored: id is empty",
                "sec_data.csv:6: Row ignored: expected 5 fields, found 4",
                "sec_data.csv:2: Row ignored: price 'abc' is not a number",
                "sec_data.csv:3: Row ignored: free_float 1.5 is not in (0, 1]",
                "sec_data.csv:4: Row ignored: price -1 is not positive",
                "sec_data.csv:5: Row ignored: shares 10.5 is not a positive whole number");
    }

    @Test
    void keepsIdenticalDuplicateSecurityDataButDropsConflicts() throws IOException {
        writeFiles(
                "date;id\n",
                """
                        id;date;price;free_float;shares
                        1;2026-09-10;10;0.5;100
                        1;2026-09-10;10.00;0.50;100
                        2;2026-09-10;10;0.5;100
                        2;2026-09-10;11;0.5;100
                        2;2026-09-21;;0.5;100
                        """,
                "id\n");

        InputData data = loader.load(dir);

        assertThat(data.securityData("1", CUT_OFF)).isPresent();
        assertThat(data.securityData("2", CUT_OFF)).isEmpty();
        assertThat(data.securityData("2", REVIEW)).get()
                .satisfies(d -> assertThat(d.freeFloat()).isEqualTo(new BigDecimal("0.5")));
        assertThat(data.warnings()).extracting(DataQualityWarning::toString).containsExactly(
                "sec_data.csv:5: Conflicting data for 2 on 2026-09-10; all rows for that date ignored",
                "sec_data.csv: 1 duplicate row(s) ignored");
    }

    @Test
    void failsOnMissingColumn() throws IOException {
        writeFiles("date;id\n", "id;date;price;shares\n", "id\n");

        assertThatThrownBy(() -> loader.load(dir))
                .isInstanceOf(InputDataException.class)
                .hasMessageContaining("sec_data.csv is missing column(s) [free_float]");
    }

    @Test
    void failsOnMissingFile() {
        assertThatThrownBy(() -> loader.load(dir))
                .isInstanceOf(InputDataException.class)
                .hasMessageContaining("Input file not found")
                .hasMessageContaining(InputDataLoader.UNIVERSE_FILE);
    }

    /** Writes the three input files with a UTF-8 byte order mark, as delivered. */
    private void writeFiles(String universe, String securityData, String composition) throws IOException {
        write(InputDataLoader.UNIVERSE_FILE, universe);
        write(InputDataLoader.SECURITY_DATA_FILE, securityData);
        write(InputDataLoader.COMPOSITION_FILE, composition);
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), "﻿" + content, StandardCharsets.UTF_8);
    }
}
