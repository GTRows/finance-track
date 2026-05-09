package com.fintrack.analytics.correlation.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response wrapper for the asset correlation matrix endpoint. The matrix is square (N x N) and
 * symmetric with {@code matrix[i][i] == 1.0} for valid self-correlation rows or {@code null} when
 * the underlying series is degenerate (stddev = 0 or fewer than 2 returns).
 *
 * <p>{@code matrix[i][j]} is the correlation coefficient between {@code assetIds[i]} and {@code
 * assetIds[j]}. {@code dataPoints[i][j]} is the count of overlapping log-return samples used to
 * compute the cell (i.e. the size of the pair-wise date intersection minus one for the differencing
 * step). Pair-wise intersection is the deliberate sparse-data alignment policy: forward-filling
 * would bias correlations toward 1.0 because flat days masquerade as zero-return days.
 */
public record CorrelationMatrixResponse(
        List<UUID> assetIds,
        List<String> assetSymbols,
        List<String> assetNames,
        List<List<Double>> matrix,
        List<List<Integer>> dataPoints,
        SamplePeriod samplePeriod,
        String method) {}
