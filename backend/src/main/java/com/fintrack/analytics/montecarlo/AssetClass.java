package com.fintrack.analytics.montecarlo;

/**
 * Macro asset class taxonomy used by the Monte Carlo simulation. Conceptually larger than {@code
 * Asset.AssetType}: includes BOND and CASH which the asset master does not model. Operator selects
 * weights per class manually; the simulation does not auto-derive weights from holdings (see
 * 29-03-PLAN "Decisions Made"). Order is the canonical UI order.
 */
public enum AssetClass {
    STOCK,
    BOND,
    CASH,
    CRYPTO,
    GOLD,
    FUND,
    CURRENCY,
    OTHER
}
