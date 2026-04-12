package com.fintrack.budget.dto;

import com.fintrack.common.entity.MonthlySummary;

import java.math.BigDecimal;
import java.time.Instant;

public record MonthlySummaryResponse(
        String period,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net,
        BigDecimal savingsRate,
        String notes,
        Instant createdAt
) {

    public static MonthlySummaryResponse from(MonthlySummary s) {
        BigDecimal net = s.getTotalIncome().subtract(s.getTotalExpense());
        return new MonthlySummaryResponse(
                s.getPeriod(),
                s.getTotalIncome(),
                s.getTotalExpense(),
                net,
                s.getSavingsRate(),
                s.getNotes(),
                s.getCreatedAt()
        );
    }
}
