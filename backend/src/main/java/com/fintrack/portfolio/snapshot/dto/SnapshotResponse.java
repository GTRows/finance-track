package com.fintrack.portfolio.snapshot.dto;

import com.fintrack.common.entity.PortfolioSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** A single point on the portfolio history chart. All monetary values are in TRY. */
public record SnapshotResponse(
        LocalDate date,
        BigDecimal totalValueTry,
        BigDecimal totalCostTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent) {

    /** Builds a response from an entity and computes derived P&L fields. */
    public static SnapshotResponse from(PortfolioSnapshot snapshot) {
        BigDecimal value =
                snapshot.getTotalValueTry() != null ? snapshot.getTotalValueTry() : BigDecimal.ZERO;
        BigDecimal cost =
                snapshot.getTotalCostTry() != null ? snapshot.getTotalCostTry() : BigDecimal.ZERO;
        BigDecimal pnl = value.subtract(cost);
        BigDecimal pnlPercent =
                cost.signum() > 0 ? pnl.divide(cost, 6, RoundingMode.HALF_UP) : null;

        return new SnapshotResponse(snapshot.getSnapshotDate(), value, cost, pnl, pnlPercent);
    }
}
