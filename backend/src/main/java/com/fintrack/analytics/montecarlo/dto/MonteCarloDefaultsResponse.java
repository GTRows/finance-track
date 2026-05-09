package com.fintrack.analytics.montecarlo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response body for {@code GET /api/v1/analytics/monte-carlo/defaults}. Carries the YAML-backed
 * iteration / horizon / weight defaults plus the per-class mean / stddev tuples so the frontend can
 * pre-fill the editor table without configuration.
 */
public record MonteCarloDefaultsResponse(
        int defaultIterations,
        int defaultHorizonYears,
        BigDecimal defaultMonthlyContribution,
        BigDecimal defaultCurrentNetWorth,
        BigDecimal defaultTargetNetWorth,
        List<AllocationClassDefault> classes) {}
