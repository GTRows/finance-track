package com.fintrack.report.capitalgains;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CapitalGainsResponse(
        Integer year,
        BigDecimal totalProceeds,
        BigDecimal totalCostBasis,
        BigDecimal totalFees,
        BigDecimal realizedGain,
        BigDecimal dividendsNetTry,
        List<YearSummary> byYear,
        List<Event> events
) {

    public record YearSummary(
            int year,
            BigDecimal proceeds,
            BigDecimal costBasis,
            BigDecimal fees,
            BigDecimal realizedGain,
            BigDecimal dividendsNetTry,
            int eventCount
    ) {}

    public record Event(
            UUID transactionId,
            UUID portfolioId,
            String portfolioName,
            UUID assetId,
            String assetSymbol,
            String assetName,
            LocalDate txnDate,
            BigDecimal quantity,
            BigDecimal pricePerUnit,
            BigDecimal proceeds,
            BigDecimal costBasis,
            BigDecimal fee,
            BigDecimal realizedGain
    ) {}
}
