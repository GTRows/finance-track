package com.fintrack.analytics.benchmark.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BenchmarkSeries(
        String code,
        String symbol,
        String currency,
        List<Point> points
) {
    public record Point(LocalDate date, BigDecimal close) {}
}
