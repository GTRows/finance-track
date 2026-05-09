package com.fintrack.portfolio.rebalance.dto;

import java.util.List;
import java.util.UUID;

/** Response body for {@code POST /api/v1/portfolios/{portfolioId}/rebalance/commit}. */
public record RebalanceCommitResult(
        UUID proposalId, int committedCount, List<UUID> transactionIds) {}
