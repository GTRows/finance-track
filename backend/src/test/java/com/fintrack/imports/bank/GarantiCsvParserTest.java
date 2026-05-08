package com.fintrack.imports.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GarantiCsvParserTest {

    private final GarantiCsvParser parser = new GarantiCsvParser();

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
    void bank_returnsGaranti() {
        assertThat(parser.bank()).isEqualTo(Bank.GARANTI);
    }

    @Test
    void parses_validFile_returnsExpectedRows() {
        List<RawBankRow> rows;
        try (InputStream is = load("garanti-2025-01.csv")) {
            rows = parser.parse(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(rows).hasSizeGreaterThanOrEqualTo(5);
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(rows.get(0).signedAmount()).isEqualByComparingTo("-1234.56");
    }

    @Test
    void parses_emptyFile_throws() {
        assertThatThrownBy(() -> parser.parse(from(""), StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void parses_missingHeader_throws() {
        String content = "foo;bar;baz\n01/01/2025;something;100,00\n";
        assertThatThrownBy(() -> parser.parse(from(content), StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header");
    }

    @Test
    void parses_unparseableDate_addsWarningButContinues() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                INVALID-DATE;BAD;-50,00;100,00
                02/01/2025;OK;-100,00;0,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).warning()).isEqualTo("Unparseable date");
        assertThat(rows.get(1).warning()).isNull();
    }

    @Test
    void parses_unparseableAmount_addsWarningButContinues() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02/01/2025;BAD;abc;0,00
                03/01/2025;OK;-100,00;-100,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).warning()).isEqualTo("Unparseable amount");
        assertThat(rows.get(1).warning()).isNull();
    }

    @Test
    void parses_negativeAmount_returnsExpense() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02/01/2025;EXP;-1.234,56;0,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).signedAmount().signum()).isLessThan(0);
        assertThat(rows.get(0).signedAmount()).isEqualByComparingTo(new BigDecimal("-1234.56"));
    }

    @Test
    void parses_positiveAmount_returnsIncome() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02/01/2025;INC;25.000,00;25.000,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows.get(0).signedAmount().signum()).isGreaterThan(0);
    }

    @Test
    void parses_footerLine_isIgnored() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02/01/2025;OK;-1,00;0,00
                Sayfa Sonu Bakiye;;;0,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        // Footer parses neither date nor amount and is dropped silently.
        assertThat(rows).hasSize(1);
    }

    @Test
    void parses_quotedCellWithDelimiter_handled() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02/01/2025;"DESC; WITH SEMICOLON";-100,00;0,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).description()).isEqualTo("DESC; WITH SEMICOLON");
        assertThat(rows.get(0).signedAmount()).isEqualByComparingTo("-100");
    }
}
