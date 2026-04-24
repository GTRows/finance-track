package com.fintrack.budget.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BudgetSummaryResponse(
        String period,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net,
        BigDecimal savingsRate,
        List<CategoryAmount> incomeByCategory,
        List<CategoryAmount> expenseByCategory) {

    public record CategoryAmount(
            UUID categoryId,
            String categoryName,
            String categoryColor,
            BigDecimal amount,
            BigDecimal percent,
            BigDecimal baseBudget,
            BigDecimal rolloverAmount,
            BigDecimal effectiveBudget) {}
}
