package com.fintrack.portfolio.holding.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for adding a new holding to a portfolio.
 */
public record AddHoldingRequest(
        @NotNull(message = "Asset is required")
        UUID assetId,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.00000001", message = "Quantity must be greater than zero")
        @Digits(integer = 12, fraction = 8, message = "Quantity must have at most 12 integer and 8 fractional digits")
        BigDecimal quantity,

        @DecimalMin(value = "0.0", inclusive = true, message = "Average cost cannot be negative")
        @Digits(integer = 16, fraction = 4, message = "Average cost must have at most 16 integer and 4 fractional digits")
        BigDecimal avgCostTry
) {
}
