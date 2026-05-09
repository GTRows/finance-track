package com.fintrack.portfolio.rebalance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/portfolios/{portfolioId}/rebalance/preview}.
 *
 * @param accountId the account that funds BUY suggestions and absorbs SELL proceeds. Must be owned
 *     by the caller and not archived.
 * @param driftThresholdOverride optional per-call override of the user's stored drift tolerance.
 *     When null, the service falls back to {@code UserSettings.rebalanceDriftThresholdPercent}.
 */
public record RebalancePreviewRequest(
        @NotNull UUID accountId,
        @DecimalMin("0.10") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
                BigDecimal driftThresholdOverride) {}
