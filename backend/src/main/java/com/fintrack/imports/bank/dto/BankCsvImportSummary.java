package com.fintrack.imports.bank.dto;

import java.util.List;

/**
 * Aggregate import summary returned by both preview and commit. {@code importedRows} is always 0
 * for preview; {@code skippedRows} counts rows that the commit path declined to persist (warnings +
 * duplicates).
 */
public record BankCsvImportSummary(
        int totalRows,
        int importedRows,
        int skippedRows,
        int duplicateRows,
        int warningRows,
        List<BankCsvPreviewRow> rows) {}
