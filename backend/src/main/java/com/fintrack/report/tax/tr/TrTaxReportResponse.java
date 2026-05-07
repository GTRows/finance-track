package com.fintrack.report.tax.tr;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response payload for {@code GET /api/v1/reports/tax/tr}.
 *
 * <p>Aggregates the year's dividend stoppage rollup, the realized capital-gains figure scoped to
 * the parameters' applicable asset classes, and the threshold comparison status for the owner's
 * annual filing decision. {@code parameters} is {@code null} when no entry exists for the requested
 * year — in that case the threshold block carries {@code "unknown"} status and a {@code
 * parameters-missing} warning surfaces in {@link #warnings()}.
 */
public record TrTaxReportResponse(
        int year,
        TrTaxParameters parameters,
        DividendStoppage dividendStoppage,
        CapitalGainsThreshold capitalGains,
        List<String> warnings) {

    public record DividendStoppage(
            BigDecimal totalGrossTry,
            BigDecimal totalWithholdingTry,
            BigDecimal totalNetTry,
            List<AssetStoppage> byAsset) {}

    public record AssetStoppage(
            UUID assetId,
            String assetSymbol,
            String assetName,
            BigDecimal grossTry,
            BigDecimal withholdingTry,
            BigDecimal netTry) {}

    public record CapitalGainsThreshold(
            BigDecimal realizedTry,
            BigDecimal thresholdTry,
            BigDecimal headroomTry,
            BigDecimal usedRatio,
            String status) {}
}
