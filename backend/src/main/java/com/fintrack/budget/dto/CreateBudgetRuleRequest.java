package com.fintrack.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetRuleRequest(
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal monthlyLimitTry
) {
}
