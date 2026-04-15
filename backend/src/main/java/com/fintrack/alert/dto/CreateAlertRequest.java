package com.fintrack.alert.dto;

import com.fintrack.common.entity.PriceAlert;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAlertRequest(
        @NotNull UUID assetId,
        @NotNull PriceAlert.Direction direction,
        @NotNull @Positive BigDecimal thresholdTry
) {
}
