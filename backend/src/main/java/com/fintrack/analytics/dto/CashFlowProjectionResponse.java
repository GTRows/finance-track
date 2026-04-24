package com.fintrack.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowProjectionResponse(
        BigDecimal avgMonthlyIncome,
        BigDecimal avgMonthlyExpense,
        BigDecimal avgMonthlyNet,
        int sampleMonths,
        boolean sufficient,
        BigDecimal startingBalance,
        List<MonthPoint> months) {

    public record MonthPoint(
            String period,
            BigDecimal projectedIncome,
            BigDecimal projectedExpense,
            BigDecimal net,
            BigDecimal endingBalance,
            BigDecimal scheduledIncome,
            BigDecimal scheduledExpense,
            List<ScheduledItem> scheduled) {}

    public record ScheduledItem(String source, String label, String kind, BigDecimal amount) {}
}
