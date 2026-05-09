package com.fintrack.analytics.montecarlo.dto;

import com.fintrack.analytics.montecarlo.AssetClass;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * One row of the Monte Carlo allocation editor. {@code annualMeanReturn} and {@code annualStdDev}
 * are nullable: a missing value means "fall back to the YAML default for this class". Service-side
 * resolution echoes the resolved tuple in {@code MonteCarloResponse.defaultsApplied}.
 */
public record AllocationClassInput(
        @NotNull AssetClass assetClass,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal weight,
        BigDecimal annualMeanReturn,
        @DecimalMin("0.0001") BigDecimal annualStdDev) {}
