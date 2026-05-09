package com.fintrack.analytics.montecarlo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response body for {@code POST /api/v1/analytics/monte-carlo}. {@code defaultsApplied} echoes the
 * resolved per-class tuple the simulation actually used, so the frontend can show "computed using
 * these defaults" when an allocation row omitted mean / stddev.
 */
public record MonteCarloResponse(
        int horizonYears,
        int iterations,
        BigDecimal currentNetWorth,
        BigDecimal monthlyContribution,
        BigDecimal targetNetWorth,
        List<YearPercentilePoint> fan,
        MonteCarloSummary summary,
        List<AllocationClassDefault> defaultsApplied) {}
