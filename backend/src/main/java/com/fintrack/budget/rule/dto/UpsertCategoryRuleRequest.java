package com.fintrack.budget.rule.dto;

import com.fintrack.common.entity.BudgetTransaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpsertCategoryRuleRequest(
        @NotBlank @Size(max = 200) String pattern,
        @NotNull UUID categoryId,
        @NotNull BudgetTransaction.TxnType txnType,
        @PositiveOrZero Integer priority) {}
