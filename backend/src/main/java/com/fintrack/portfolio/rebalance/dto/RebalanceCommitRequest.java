package com.fintrack.portfolio.rebalance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request body for {@code POST /api/v1/portfolios/{portfolioId}/rebalance/commit}. */
public record RebalanceCommitRequest(
        @NotNull UUID proposalId,
        @NotNull UUID accountId,
        @NotEmpty @Size(max = 50) List<@NotNull @Min(0) Integer> selectedIndices) {}
