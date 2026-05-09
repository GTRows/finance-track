package com.fintrack.analytics.montecarlo.dto;

import java.math.BigDecimal;

/**
 * One year boundary on the percentile fan chart. {@code year} is 1-indexed (the simulation captures
 * end-of-year values; no year-0 point).
 */
public record YearPercentilePoint(
        int year, BigDecimal p10, BigDecimal p25, BigDecimal p50, BigDecimal p75, BigDecimal p90) {}
