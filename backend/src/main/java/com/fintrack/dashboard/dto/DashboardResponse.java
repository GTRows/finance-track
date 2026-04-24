package com.fintrack.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        BigDecimal totalNetWorth,
        List<PortfolioSummary> portfolios,
        BudgetOverview budget,
        List<UpcomingBill> upcomingBills) {

    public record PortfolioSummary(
            UUID id,
            String name,
            String portfolioType,
            BigDecimal valueTry,
            BigDecimal costTry,
            BigDecimal pnlTry,
            BigDecimal pnlPercent) {}

    public record BudgetOverview(
            String period,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net,
            BigDecimal savingsRate) {}

    public record UpcomingBill(
            UUID id,
            String name,
            BigDecimal amount,
            int dueDay,
            long daysUntilDue,
            String status) {}
}
