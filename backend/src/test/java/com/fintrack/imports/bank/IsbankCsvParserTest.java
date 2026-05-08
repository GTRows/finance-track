package com.fintrack.imports.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IsbankCsvParserTest {

    private final IsbankCsvParser parser = new IsbankCsvParser();

    private InputStream load(String name) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("bank-csv/" + name);
        if (is == null) {
            throw new IllegalStateException("Fixture not found: " + name);
        }
        return is;
    }

    private InputStream from(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void bank_returnsIsbank() {
        assertThat(parser.bank()).isEqualTo(Bank.ISBANK);
    }

    @Test
    void parses_internationalFormat_returnsExpectedRows() {
        List<RawBankRow> rows;
        try (InputStream is = load("isbank-international-2025-01.csv")) {
            rows = parser.parse(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(rows).isNotEmpty();
        RawBankRow first = rows.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(first.signedAmount()).isEqualByComparingTo("-1234.56");
    }

    @Test
    void parses_legacyFormat_returnsExpectedRows() {
        List<RawBankRow> rows;
        try (InputStream is = load("isbank-legacy-2025-01.csv")) {
            rows = parser.parse(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).signedAmount()).isEqualByComparingTo("-1234.56");
        assertThat(rows.get(1).signedAmount()).isEqualByComparingTo("25000.00");
    }

    @Test
    void parses_debitColumn_returnsNegativeAmount() {
        String content =
                """
                Tarih,Aciklama,Borc,Alacak
                2025-01-02,EXP,100.00,
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).signedAmount().signum()).isLessThan(0);
    }

    @Test
    void parses_creditColumn_returnsPositiveAmount() {
        String content =
                """
                Tarih,Aciklama,Borc,Alacak
                2025-01-02,INC,,500.00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).signedAmount().signum()).isGreaterThan(0);
    }

    @Test
    void parses_bothColumnsPopulated_addsWarning() {
        String content =
                """
                Tarih,Aciklama,Borc,Alacak
                2025-01-02,DOUBLE,100.00,200.00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).warning()).contains("Both");
    }

    @Test
    void parses_bothColumnsEmpty_addsWarning() {
        String content =
                """
                Tarih,Aciklama,Borc,Alacak
                2025-01-02,EMPTY,,
                """;
        // bothColumnsEmpty rows whose date AND amounts are all blank get dropped.
        // Use a row that has a date but no amounts.
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).warning()).contains("Missing");
    }

    @Test
    void parses_unknownDateFormat_addsWarning() {
        String content =
                """
                Tarih,Aciklama,Borc,Alacak
                INVALID-DATE,BAD,50.00,
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).warning()).isEqualTo("Unparseable date");
    }

    @Test
    void parses_emptyFile_throws() {
        assertThatThrownBy(() -> parser.parse(from(""), StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void parses_missingHeader_throws() {
        String content = "foo,bar,baz\n2025-01-01,desc,100.00\n";
        assertThatThrownBy(() -> parser.parse(from(content), StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header");
    }
}
