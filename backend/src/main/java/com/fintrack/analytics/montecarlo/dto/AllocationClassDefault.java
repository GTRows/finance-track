package com.fintrack.analytics.montecarlo.dto;

import com.fintrack.analytics.montecarlo.AssetClass;
import java.math.BigDecimal;

/**
 * Per-class defaults the simulation either consulted or that the defaults endpoint exposes for the
 * frontend's pre-fill on the editor table.
 */
public record AllocationClassDefault(
        AssetClass assetClass,
        BigDecimal defaultWeight,
        BigDecimal annualMeanReturn,
        BigDecimal annualStdDev) {}
