package com.fintrack.analytics.compare.dto;

import java.util.List;

/**
 * Response wrapper for the multi-portfolio comparison endpoint. The {@code currency} field is the
 * literal {@code "TRY"} for v1 — every {@link PortfolioComparisonPoint} value is already
 * pre-aggregated in TRY by the snapshot pipeline.
 */
public record PortfolioComparisonResponse(
        String currency, List<PortfolioComparisonSeries> series) {}
