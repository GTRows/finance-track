package com.fintrack.imports.dto;

import java.util.List;

public record ImportSummary(
        int totalRows,
        int importedRows,
        int skippedRows,
        int warningRows,
        List<ImportPreviewRow> rows
) {
}
