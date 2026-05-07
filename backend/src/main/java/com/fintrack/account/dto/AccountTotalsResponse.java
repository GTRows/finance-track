package com.fintrack.account.dto;

import java.math.BigDecimal;
import java.util.List;

/** Aggregate balance rollup for the accounts page stat strip. */
public record AccountTotalsResponse(
        int liveCount, int archivedCount, List<CurrencyTotal> byCurrency) {

    /** Single per-currency rollup row. */
    public record CurrencyTotal(String currency, BigDecimal totalBalance) {}
}
