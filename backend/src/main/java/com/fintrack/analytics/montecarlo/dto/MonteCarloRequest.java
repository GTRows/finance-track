package com.fintrack.analytics.montecarlo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/analytics/monte-carlo}. {@code targetNetWorth} is optional:
 * when null the success-probability summary stat is omitted.
 *
 * <p>{@link #normalisedHash()} produces a stable cache-key fragment that is invariant under
 * allocation row reordering: rows are sorted by {@code AssetClass.name()} before hashing so two
 * equivalent requests with different row order share the same Caffeine entry.
 */
public record MonteCarloRequest(
        @NotNull @Min(1) @Max(50) Integer horizonYears,
        @NotNull @Min(1) @Max(10000) Integer iterations,
        @NotNull @DecimalMin("0.0") BigDecimal currentNetWorth,
        @NotNull @DecimalMin("0.0") BigDecimal monthlyContribution,
        @DecimalMin("0.0") BigDecimal targetNetWorth,
        @NotEmpty @Valid List<AllocationClassInput> allocations) {

    /** Stable cache-key fragment, invariant under allocation row reordering. */
    public String normalisedHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(horizonYears).append(':').append(iterations).append(':');
        sb.append(currentNetWorth.toPlainString()).append(':');
        sb.append(monthlyContribution.toPlainString()).append(':');
        sb.append(targetNetWorth == null ? "null" : targetNetWorth.toPlainString()).append(':');
        allocations.stream()
                .sorted(Comparator.comparing(a -> a.assetClass().name()))
                .forEach(
                        a ->
                                sb.append(a.assetClass().name())
                                        .append('=')
                                        .append(a.weight().toPlainString())
                                        .append('/')
                                        .append(
                                                a.annualMeanReturn() == null
                                                        ? "null"
                                                        : a.annualMeanReturn().toPlainString())
                                        .append('/')
                                        .append(
                                                a.annualStdDev() == null
                                                        ? "null"
                                                        : a.annualStdDev().toPlainString())
                                        .append('|'));
        return sb.toString();
    }
}
