package com.fintrack.budget.dto;

import com.fintrack.common.entity.BudgetTransaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull BudgetTransaction.TxnType txnType,
        @NotNull @Positive BigDecimal amount,
        String currency,
        UUID categoryId,
        String description,
        @NotNull LocalDate txnDate,
        boolean isRecurring,
        List<UUID> tagIds) {}
