package com.fintrack.analytics.compare.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day in a portfolio comparison series. Monetary fields are TRY-denominated to match the
 * underlying {@link com.fintrack.common.entity.PortfolioSnapshot} aggregation. {@code
 * realizedPnlTry} is computed via the running-average approximation: it sums {@code (priceTry -
 * currentAvgCostTry) * quantity} for SELL transactions whose {@code txnDate <= date}. This is a v1
 * approximation of FIFO; the precise lot-level capital-gains calculation lives in {@code
 * /api/v1/reports/capital-gains}.
 */
public record PortfolioComparisonPoint(
        LocalDate date,
        BigDecimal totalValueTry,
        BigDecimal totalCostTry,
        BigDecimal unrealizedPnlTry,
        BigDecimal realizedPnlTry,
        BigDecimal totalPnlTry) {}
