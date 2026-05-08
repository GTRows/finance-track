package com.fintrack.imports.bank;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Parser-level row shape produced by every {@link BankCsvParser}. The {@code signedAmount} carries
 * debit as negative and credit as positive (the parser does the sign normalisation regardless of
 * the bank's column convention). {@code warning} is non-null when the row failed to parse cleanly;
 * the import service surfaces these in preview and skips them at commit.
 */
public record RawBankRow(
        int rowNumber,
        LocalDate date,
        BigDecimal signedAmount,
        BigDecimal balanceAfter,
        String counterparty,
        String description,
        String warning) {}
