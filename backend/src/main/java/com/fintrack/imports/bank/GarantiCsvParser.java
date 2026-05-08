package com.fintrack.imports.bank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Garanti BBVA current-account CSV export parser.
 *
 * <p>Format quirks: Windows-1254 encoding (Excel TR locale default; the export option labelled "CSV
 * (Windows)" produces this). Delimiter {@code ;}. Date {@code dd/MM/yyyy}. Decimal {@code ,}
 * (comma) with {@code .} as thousands grouping. Sign convention: SINGLE column {@code Tutar} with
 * negative for debit. Header at row 1 (no metadata block). Possible footer rows for "Sayfa Sonu
 * Bakiye" -- ignored because they do not parse to (date, amount).
 */
@Component
@Slf4j
public class GarantiCsvParser implements BankCsvParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final char DELIMITER = ';';
    private static final Charset CHARSET = Charset.forName("windows-1254");

    @Override
    public Bank bank() {
        return Bank.GARANTI;
    }

    @Override
    public Charset charset() {
        return CHARSET;
    }

    @Override
    public List<RawBankRow> parse(InputStream input) {
        return parse(input, CHARSET);
    }

    /** Test-only overload: lets unit tests feed UTF-8 fixtures. */
    public List<RawBankRow> parse(InputStream input, Charset charset) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset))) {
            List<String> lines =
                    reader.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("File is empty");
            }
            int headerIdx = findHeader(lines);
            if (headerIdx < 0) {
                throw new IllegalArgumentException(
                        "Could not locate header row (expected 'Tarih')");
            }
            String[] header = splitLine(lines.get(headerIdx));
            int colDate = findColumn(header, "Tarih");
            int colDesc = findColumn(header, "Aciklama");
            int colAmount = findColumn(header, "Tutar");
            int colBalance = findColumn(header, "Bakiye");
            if (colDate < 0 || colAmount < 0) {
                throw new IllegalArgumentException("Required columns not found (Tarih, Tutar)");
            }
            List<RawBankRow> rows = new ArrayList<>();
            for (int i = headerIdx + 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] cells = splitLine(line);
                if (cells.length <= colAmount) {
                    continue;
                }
                LocalDate date = parseDate(cells[colDate]);
                BigDecimal amount = parseAmount(cells[colAmount]);
                BigDecimal balance =
                        colBalance >= 0 && colBalance < cells.length
                                ? parseAmount(cells[colBalance])
                                : null;
                String desc = colDesc >= 0 && colDesc < cells.length ? cells[colDesc] : "";
                if (date == null && amount == null) {
                    continue;
                }
                String warn = null;
                if (date == null) {
                    warn = "Unparseable date";
                } else if (amount == null) {
                    warn = "Unparseable amount";
                }
                rows.add(new RawBankRow(i + 1, date, amount, balance, desc, desc, warn));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read CSV: " + e.getMessage(), e);
        }
    }

    private static int findHeader(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toLowerCase(Locale.ROOT).contains("tarih")) {
                return i;
            }
        }
        return -1;
    }

    private static int findColumn(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String normalised = raw.trim().replace(".", "").replace(",", ".");
            return new BigDecimal(normalised);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Splits a CSV line on {@link #DELIMITER}, honouring double-quote-wrapped cells that contain
     * the delimiter literally. Trims surrounding quotes from each cell.
     */
    static String[] splitLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == DELIMITER && !inQuotes) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }
}
