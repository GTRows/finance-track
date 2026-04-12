package com.fintrack.asset.dto;

import com.fintrack.common.entity.Asset;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Asset master-data response sent to the client.
 */
public record AssetResponse(
        UUID id,
        String symbol,
        String name,
        Asset.AssetType assetType,
        String currency,
        BigDecimal price,
        BigDecimal priceUsd,
        Instant priceUpdatedAt
) {

    /** Maps an {@link Asset} entity to a response DTO. */
    public static AssetResponse from(Asset a) {
        return new AssetResponse(
                a.getId(),
                a.getSymbol(),
                a.getName(),
                a.getAssetType(),
                a.getCurrency(),
                a.getPrice(),
                a.getPriceUsd(),
                a.getPriceUpdatedAt()
        );
    }
}
