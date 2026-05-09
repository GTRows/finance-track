package com.fintrack.analytics.montecarlo.dto;

import java.math.BigDecimal;

/**
 * Headline summary stats for the simulation. {@code successProbability} is null when the request
 * omitted {@code targetNetWorth}.
 */
public record MonteCarloSummary(
        BigDecimal mean,
        BigDecimal p10,
        BigDecimal p50,
        BigDecimal p90,
        BigDecimal successProbability) {}
