package com.fintrack.analytics.correlation.dto;

import java.time.LocalDate;

/**
 * The bounded sampling window used by a correlation matrix response. {@code alignedDays} is the
 * count of dates where ALL requested assets had a price-history row (the global N-way intersection
 * size, useful as a "headline" number above the matrix; per-pair counts are returned in {@code
 * dataPoints}).
 */
public record SamplePeriod(LocalDate from, LocalDate to, int alignedDays) {}
