package com.fintrack.budget.recurring.dto;

import com.fintrack.common.entity.BudgetTransaction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record UpsertRecurringRequest(
        @NotNull BudgetTransaction.TxnType txnType,
        @NotNull @Positive BigDecimal amount,
        UUID categoryId,
        String description,
        @NotNull @Min(1) @Max(31) Integer dayOfMonth,
        Boolean active) {}
