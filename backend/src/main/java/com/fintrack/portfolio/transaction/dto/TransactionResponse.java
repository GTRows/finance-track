package com.fintrack.portfolio.transaction.dto;

import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID portfolioId,
        UUID assetId,
        String assetSymbol,
        String assetName,
        TxnType txnType,
        BigDecimal quantity,
        BigDecimal priceTry,
        BigDecimal amountTry,
        BigDecimal feeTry,
        String notes,
        LocalDate txnDate,
        Instant createdAt) {

    public static TransactionResponse from(InvestmentTransaction t, Asset asset) {
        return new TransactionResponse(
                t.getId(),
                t.getPortfolioId(),
                t.getAssetId(),
                asset != null ? asset.getSymbol() : null,
                asset != null ? asset.getName() : null,
                t.getTxnType(),
                t.getQuantity(),
                t.getPriceTry(),
                t.getAmountTry(),
                t.getFeeTry(),
                t.getNotes(),
                t.getTxnDate(),
                t.getCreatedAt());
    }
}
