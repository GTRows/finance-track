package com.fintrack.alert.dto;

import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PriceAlert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID assetId,
        String assetSymbol,
        String assetName,
        Asset.AssetType assetType,
        BigDecimal currentPriceTry,
        PriceAlert.Direction direction,
        BigDecimal thresholdTry,
        PriceAlert.Status status,
        Instant createdAt,
        Instant triggeredAt
) {

    public static AlertResponse from(PriceAlert alert) {
        Asset asset = alert.getAsset();
        return new AlertResponse(
                alert.getId(),
                asset.getId(),
                asset.getSymbol(),
                asset.getName(),
                asset.getAssetType(),
                asset.getPrice(),
                alert.getDirection(),
                alert.getThresholdTry(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getTriggeredAt()
        );
    }
}
