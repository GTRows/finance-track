package com.fintrack.networth.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Combined net-worth timeline across all of the user's active portfolios,
 * plus user-authored annotations overlaid on the chart.
 */
public record NetWorthTimelineResponse(
        List<Point> series,
        List<NetWorthEventResponse> events
) {
    public record Point(LocalDate date, BigDecimal totalValueTry, BigDecimal totalCostTry) {}
}
