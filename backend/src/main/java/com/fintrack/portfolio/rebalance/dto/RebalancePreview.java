package com.fintrack.portfolio.rebalance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response body for {@code POST /api/v1/portfolios/{portfolioId}/rebalance/preview}. */
public record RebalancePreview(
        UUID proposalId,
        BigDecimal totalValueTry,
        BigDecimal accountCashTry,
        BigDecimal driftThresholdPercent,
        List<RebalanceSuggestion> suggestions,
        BigDecimal projectedDriftAfterPercent,
        List<String> summaryWarnings,
        Instant expiresAt) {}
