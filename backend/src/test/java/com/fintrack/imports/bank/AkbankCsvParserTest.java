package com.fintrack.imports.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AkbankCsvParserTest {

    private final AkbankCsvParser parser = new AkbankCsvParser();

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
    void bank_returnsAkbank() {
        assertThat(parser.bank()).isEqualTo(Bank.AKBANK);
    }

    @Test
    void parses_validFile_skipsMetadataBlock() {
        List<RawBankRow> rows;
        try (InputStream is = load("akbank-2025-01.csv")) {
            rows = parser.parse(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Metadata block + footer trimmed: only data rows surface.
        assertThat(rows).isNotEmpty();
        // First valid row has date 02.01.2025
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2025, 1, 2));
    }

    @Test
    void parses_dotDelimitedDate_handled() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02.01.2025;OK;-100,00;0,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2025, 1, 2));
    }

    @Test
    void parses_negativeAmount_returnsExpense() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02.01.2025;EXP;-1.234,56;0,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows.get(0).signedAmount().signum()).isLessThan(0);
    }

    @Test
    void parses_positiveAmount_returnsIncome() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02.01.2025;INC;25.000,00;25.000,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows.get(0).signedAmount().signum()).isGreaterThan(0);
    }

    @Test
    void parses_footerToplamLine_isIgnored() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02.01.2025;OK;-1,00;0,00
                Toplam Borc;;1,00;
                Toplam Alacak;;0,00;
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
    }

    @Test
    void parses_emptyFile_throws() {
        assertThatThrownBy(() -> parser.parse(from(""), StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void parses_missingHeaderAfterTenLines_throws() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append("metadata-").append(i).append(";value\n");
        }
        sb.append("Tarih;Aciklama;Tutar;Bakiye\n");
        sb.append("02.01.2025;OK;-1,00;0,00\n");
        assertThatThrownBy(() -> parser.parse(from(sb.toString()), StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header");
    }

    @Test
    void parses_unparseableAmount_addsWarning() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02.01.2025;BAD;abc;0,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).warning()).isEqualTo("Unparseable amount");
    }

    @Test
    void parses_quotedCellWithDelimiter_handled() {
        String content =
                """
                Tarih;Aciklama;Tutar;Bakiye
                02.01.2025;"DESC; WITH SEMICOLON";-100,00;0,00
                """;
        List<RawBankRow> rows = parser.parse(from(content), StandardCharsets.UTF_8);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).description()).isEqualTo("DESC; WITH SEMICOLON");
    }
}
