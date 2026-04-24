package com.fintrack.debt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DebtResponse(
        UUID id,
        String name,
        String debtType,
        BigDecimal principal,
        BigDecimal annualRate,
        Integer termMonths,
        LocalDate startDate,
        String notes,
        BigDecimal scheduledMonthlyPayment,
        BigDecimal totalScheduledPaid,
        BigDecimal totalActuallyPaid,
        BigDecimal remainingBalance,
        BigDecimal totalInterest,
        LocalDate scheduledPayoffDate,
        LocalDate projectedPayoffDate,
        BigDecimal progressRatio,
        Integer monthsAhead,
        String status,
        List<AmortizationRow> nextPayments) {
    public record AmortizationRow(
            LocalDate dueDate,
            BigDecimal payment,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal remainingBalance) {}
}
