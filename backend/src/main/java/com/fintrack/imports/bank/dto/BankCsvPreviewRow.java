package com.fintrack.imports.bank.dto;

import com.fintrack.common.entity.BudgetTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-row preview/commit shape carried back to the operator. {@code amount} is the absolute value
 * (always positive); the sign information lives on {@code inferredType}. {@code fingerprint} is the
 * SHA-256 hex digest used for idempotency. {@code duplicate} is true when the fingerprint already
 * exists for the target account at preview/commit time.
 */
public record BankCsvPreviewRow(
        int rowNumber,
        LocalDate date,
        BudgetTransaction.TxnType inferredType,
        BigDecimal amount,
        String description,
        String counterparty,
        UUID matchedCategoryId,
        String matchedCategoryName,
        String fingerprint,
        String warning,
        boolean duplicate) {}
