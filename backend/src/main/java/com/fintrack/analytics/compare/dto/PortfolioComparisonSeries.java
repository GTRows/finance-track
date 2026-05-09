package com.fintrack.analytics.compare.dto;

import java.util.List;
import java.util.UUID;

/** A single portfolio's chronological series in a multi-portfolio comparison response. */
public record PortfolioComparisonSeries(
        UUID portfolioId, String name, List<PortfolioComparisonPoint> points) {}
