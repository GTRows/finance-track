package com.fintrack.budget.dto;

import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String icon,
        String color,
        BigDecimal budgetAmount
) {

    public static CategoryResponse from(IncomeCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getIcon(), c.getColor(), null);
    }

    public static CategoryResponse from(ExpenseCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getIcon(), c.getColor(), c.getBudgetAmount());
    }
}
