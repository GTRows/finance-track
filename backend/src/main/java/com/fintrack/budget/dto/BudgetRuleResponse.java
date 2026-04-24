package com.fintrack.budget.dto;

import com.fintrack.common.entity.BudgetRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetRuleResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categoryColor,
        BigDecimal monthlyLimitTry,
        BigDecimal currentSpendTry,
        BigDecimal usagePct,
        boolean active,
        String lastAlertedPeriod,
        Instant createdAt) {

    public static BudgetRuleResponse from(
            BudgetRule rule,
            String categoryName,
            String categoryColor,
            BigDecimal currentSpendTry,
            BigDecimal usagePct) {
        return new BudgetRuleResponse(
                rule.getId(),
                rule.getCategoryId(),
                categoryName,
                categoryColor,
                rule.getMonthlyLimitTry(),
                currentSpendTry,
                usagePct,
                rule.isActive(),
                rule.getLastAlertedPeriod(),
                rule.getCreatedAt());
    }
}
