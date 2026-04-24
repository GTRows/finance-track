package com.fintrack.portfolio.risk.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Risk/return summary derived from a portfolio's daily value snapshots. All percentage-style values
 * are expressed as decimals (0.12 = 12%).
 */
public record RiskMetricsResponse(
        int snapshotCount,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalReturn,
        BigDecimal annualVolatility,
        BigDecimal sharpeRatio,
        BigDecimal maxDrawdown,
        BigDecimal bestDay,
        BigDecimal worstDay,
        BigDecimal averageDailyReturn,
        BigDecimal riskFreeRate,
        boolean sufficientData) {
    public static RiskMetricsResponse insufficient(
            int snapshotCount, LocalDate start, LocalDate end, BigDecimal riskFreeRate) {
        return new RiskMetricsResponse(
                snapshotCount,
                start,
                end,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                riskFreeRate,
                false);
    }
}
