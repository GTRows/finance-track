package com.fintrack.analytics.benchmark.dto;

import java.util.List;

public record BenchmarkSeriesResponse(
        int days,
        List<BenchmarkSeries> series
) {}
