package com.fintrack.portfolio.allocation.dto;

import com.fintrack.common.entity.Asset;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AllocationTargetInput(
        @NotNull Asset.AssetType assetType,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal targetPercent
) {
}
