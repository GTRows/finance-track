package com.fintrack.portfolio.rebalance.dto;

import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row in a {@link RebalancePreview}. The {@code index} field is a stable position used by the
 * commit endpoint to identify which rows the operator ticked.
 *
 * <p>For the {@link #warning} field: {@code NO_HOLDING_TO_BUY} signals an empty underweight bucket
 * where the executor cannot recommend a specific asset; {@code QUANTITY_BELOW_LOT} signals the
 * STOCK/FUND integer-truncation zeroed the row; {@code INSUFFICIENT_HOLDING} is reserved for SELL
 * rows that exceed the holding's quantity (defensive - the projection caps at the holding value).
 */
public record RebalanceSuggestion(
        int index,
        UUID assetId,
        String symbol,
        Asset.AssetType assetType,
        InvestmentTransaction.TxnType action,
        BigDecimal quantity,
        BigDecimal estimatedPriceTry,
        BigDecimal estimatedAmountTry,
        BigDecimal currentValueTry,
        BigDecimal currentWeightPercent,
        BigDecimal targetWeightPercent,
        BigDecimal driftPercentBefore,
        String warning) {}
