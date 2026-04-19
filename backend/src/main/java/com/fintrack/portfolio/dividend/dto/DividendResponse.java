package com.fintrack.portfolio.dividend.dto;

import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Dividend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DividendResponse(
        UUID id,
        UUID portfolioId,
        UUID assetId,
        String assetSymbol,
        String assetName,
        BigDecimal amountPerShare,
        BigDecimal shares,
        BigDecimal grossAmount,
        BigDecimal withholdingTax,
        BigDecimal netAmount,
        String currency,
        BigDecimal netAmountTry,
        LocalDate paymentDate,
        LocalDate exDividendDate,
        String notes
) {
    public static DividendResponse from(Dividend d, Asset asset) {
        return new DividendResponse(
                d.getId(),
                d.getPortfolioId(),
                d.getAssetId(),
                asset != null ? asset.getSymbol() : null,
                asset != null ? asset.getName() : null,
                d.getAmountPerShare(),
                d.getShares(),
                d.getGrossAmount(),
                d.getWithholdingTax(),
                d.getNetAmount(),
                d.getCurrency(),
                d.getNetAmountTry(),
                d.getPaymentDate(),
                d.getExDividendDate(),
                d.getNotes());
    }
}
