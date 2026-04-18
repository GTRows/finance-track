package com.fintrack.fire.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FireResponse(
        BigDecimal currentNetWorth,
        BigDecimal avgMonthlyIncome,
        BigDecimal avgMonthlyExpense,
        BigDecimal savingsRate,
        BigDecimal monthlyContribution,
        BigDecimal withdrawalRate,
        BigDecimal expectedReturn,
        BigDecimal targetNumber,
        BigDecimal progressRatio,
        Integer monthsToFi,
        BigDecimal yearsToFi,
        LocalDate projectedFiDate,
        Integer samplesUsed,
        boolean sufficientData,
        List<TrajectoryPoint> trajectory
) {
    public record TrajectoryPoint(int year, LocalDate date, BigDecimal netWorth) {}
}
