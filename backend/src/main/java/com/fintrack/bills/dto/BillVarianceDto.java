package com.fintrack.bills.dto;

import java.math.BigDecimal;

/**
 * Month-over-month change between the two most recent PAID periods for a bill.
 * {@code delta} is positive when the current period was more expensive.
 */
public record BillVarianceDto(
        String currentPeriod,
        BigDecimal currentAmount,
        String previousPeriod,
        BigDecimal previousAmount,
        BigDecimal delta,
        BigDecimal deltaPercent,
        boolean flagged
) {
}
