package com.fintrack.portfolio.holding.dto;

import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PortfolioHolding;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Holding response sent to the client. Includes asset metadata so the UI does not need to make a
 * separate lookup for each row.
 */
public record HoldingResponse(
        UUID id,
        UUID portfolioId,
        UUID assetId,
        String assetSymbol,
        String assetName,
        Asset.AssetType assetType,
        BigDecimal quantity,
        BigDecimal avgCostTry,
        BigDecimal currentPriceTry,
        BigDecimal currentValueTry,
        BigDecimal costBasisTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent,
        boolean pinned,
        Instant priceUpdatedAt,
        Instant updatedAt) {

    /** Builds a response by joining a holding with its asset and computing derived fields. */
    public static HoldingResponse from(PortfolioHolding h, Asset asset) {
        BigDecimal currentPrice = asset.getPrice();
        BigDecimal quantity = h.getQuantity() != null ? h.getQuantity() : BigDecimal.ZERO;
        BigDecimal avgCost = h.getAvgCostTry();

        BigDecimal currentValue = currentPrice != null ? currentPrice.multiply(quantity) : null;

        BigDecimal costBasis = avgCost != null ? avgCost.multiply(quantity) : null;

        BigDecimal pnl =
                (currentValue != null && costBasis != null)
                        ? currentValue.subtract(costBasis)
                        : null;

        BigDecimal pnlPercent =
                (pnl != null && costBasis != null && costBasis.signum() > 0)
                        ? pnl.divide(costBasis, 6, java.math.RoundingMode.HALF_UP)
                        : null;

        return new HoldingResponse(
                h.getId(),
                h.getPortfolioId(),
                h.getAssetId(),
                asset.getSymbol(),
                asset.getName(),
                asset.getAssetType(),
                quantity,
                avgCost,
                currentPrice,
                currentValue,
                costBasis,
                pnl,
                pnlPercent,
                h.isPinned(),
                asset.getPriceUpdatedAt(),
                h.getUpdatedAt());
    }
}
