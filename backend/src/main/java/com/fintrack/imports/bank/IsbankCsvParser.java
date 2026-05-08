package com.fintrack.imports.bank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Turkiye Is Bankasi current-account CSV export parser.
 *
 * <p>Format quirks: UTF-8 encoding (modern Internet Bank CSV). Delimiter is sniffed: prefer {@code
 * ,} unless {@code ;} dominates in the first 5 lines. Date {@code yyyy-MM-dd}. Decimal {@code .}
 * for the international format / {@code ,} for the legacy format -- the parser sniffs by checking
 * which decimal pattern fits the {@code Borc}/{@code Alacak} columns. Sign convention: TWO columns
 * {@code Borc} (debit) + {@code Alacak} (credit) -- exactly one populated per row.
 */
@Component
@Slf4j
public class IsbankCsvParser implements BankCsvParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    @Override
    public Bank bank() {
        return Bank.ISBANK;
    }

    @Override
    public Charset charset() {
        return CHARSET;
    }

    @Override
    public List<RawBankRow> parse(InputStream input) {
        return parse(input, CHARSET);
    }

    /** Test-only overload. */
    public List<RawBankRow> parse(InputStream input, Charset charset) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset))) {
            List<String> lines =
                    reader.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("File is empty");
            }
            char delimiter = sniffDelimiter(lines);
            int headerIdx = findHeader(lines);
            if (headerIdx < 0) {
                throw new IllegalArgumentException(
                        "Could not locate header row (expected 'Tarih' and 'Aciklama')");
            }
            String[] header = splitLine(lines.get(headerIdx), delimiter);
            int colDate = findColumn(header, "Tarih");
            int colDesc = findColumn(header, "Aciklama");
            int colDebit = findColumn(header, "Borc");
            int colCredit = findColumn(header, "Alacak");
            int colBalance = findColumn(header, "Bakiye");
            if (colDate < 0 || colDebit < 0 || colCredit < 0) {
                throw new IllegalArgumentException(
                        "Required columns not found (Tarih, Borc, Alacak)");
            }
            boolean legacy = delimiter == ';';
            List<RawBankRow> rows = new ArrayList<>();
            for (int i = headerIdx + 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] cells = splitLine(line, delimiter);
                if (cells.length <= Math.max(colDebit, colCredit)) {
                    continue;
                }
                LocalDate date = parseDate(cells[colDate]);
                BigDecimal debit = parseAmount(cells[colDebit], legacy);
                BigDecimal credit = parseAmount(cells[colCredit], legacy);
                BigDecimal balance =
                        colBalance >= 0 && colBalance < cells.length
                                ? parseAmount(cells[colBalance], legacy)
                                : null;
                String desc = colDesc >= 0 && colDesc < cells.length ? cells[colDesc] : "";
                if (date == null && debit == null && credit == null) {
                    continue;
                }
                String warn = null;
                BigDecimal signed = null;
                if (date == null) {
                    warn = "Unparseable date";
                } else if (debit != null && credit != null) {
                    warn = "Both debit and credit columns populated";
                } else if (debit == null && credit == null) {
                    warn = "Missing amount";
                } else if (credit != null) {
                    signed = credit;
                } else {
                    signed = debit.negate();
                }
                rows.add(new RawBankRow(i + 1, date, signed, balance, desc, desc, warn));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read CSV: " + e.getMessage(), e);
        }
    }

    /** Counts {@code ,} vs {@code ;} in the first ~5 lines and picks the dominant. */
    private static char sniffDelimiter(List<String> lines) {
        int comma = 0;
        int semi = 0;
        int n = Math.min(5, lines.size());
        for (int i = 0; i < n; i++) {
            for (char c : lines.get(i).toCharArray()) {
                if (c == ',') {
                    comma++;
                } else if (c == ';') {
                    semi++;
                }
            }
        }
        return semi > comma ? ';' : ',';
    }

    private static int findHeader(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String lower = lines.get(i).toLowerCase(Locale.ROOT);
            if (lower.contains("tarih") && lower.contains("aciklama")) {
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

    private static BigDecimal parseAmount(String raw, boolean legacyDecimal) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String trimmed = raw.trim();
            String normalised;
            if (legacyDecimal) {
                normalised = trimmed.replace(".", "").replace(",", ".");
            } else {
                normalised = trimmed.replace(",", "");
            }
            return new BigDecimal(normalised);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String[] splitLine(String line, char delimiter) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == delimiter && !inQuotes) {
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
