package com.fintrack.report.tax.tr;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.dividend.DividendRepository;
import com.fintrack.report.capitalgains.CapitalGainsResponse;
import com.fintrack.report.capitalgains.CapitalGainsService;
import com.fintrack.settings.UserSettingsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates dividend stoppage and capital-gains threshold figures for the TR tax helper.
 *
 * <p>Reuses {@link CapitalGainsService#compute(UUID, Integer)} for the realized-gain and FIFO
 * lot-tracking logic and layers the threshold comparison + status banding on top. Year defaulting
 * honours the user's {@link UserSettings#getTimezone()} so an owner travelling overseas still sees
 * the right fiscal year.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrTaxService {

    private static final BigDecimal APPROACHING_RATIO = new BigDecimal("0.80");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    private static final String STATUS_UNDER = "under";
    private static final String STATUS_APPROACHING = "approaching";
    private static final String STATUS_OVER = "over";
    private static final String STATUS_UNKNOWN = "unknown";

    private static final String WARN_APPROACHING = "approaching-threshold";
    private static final String WARN_EXCEEDED = "threshold-exceeded";
    private static final String WARN_PARAMS_MISSING = "parameters-missing";

    private final TrTaxParametersLoader parametersLoader;
    private final CapitalGainsService capitalGainsService;
    private final DividendRepository dividendRepo;
    private final PortfolioRepository portfolioRepo;
    private final AssetRepository assetRepo;
    private final UserSettingsRepository userSettingsRepo;

    @Transactional(readOnly = true)
    public TrTaxReportResponse compute(UUID userId, Integer yearFilter) {
        int year = resolveYear(userId, yearFilter);
        Optional<TrTaxParameters> paramsOpt = parametersLoader.findByYear(year);

        List<Portfolio> portfolios =
                portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);
        if (portfolios.isEmpty()) {
            return zeroFilledResponse(year, paramsOpt.orElse(null));
        }

        List<UUID> portfolioIds = portfolios.stream().map(Portfolio::getId).toList();
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        TrTaxReportResponse.DividendStoppage stoppage =
                aggregateDividendStoppage(portfolioIds, from, to);

        BigDecimal realizedTry =
                computeRealizedScopedToAssetTypes(userId, year, paramsOpt.orElse(null));

        TrTaxReportResponse.CapitalGainsThreshold capitalGains =
                buildThreshold(realizedTry, paramsOpt.orElse(null));

        List<String> warnings = buildWarnings(capitalGains.status(), paramsOpt.isEmpty());

        return new TrTaxReportResponse(
                year, paramsOpt.orElse(null), stoppage, capitalGains, warnings);
    }

    private int resolveYear(UUID userId, Integer yearFilter) {
        if (yearFilter != null) return yearFilter;
        ZoneId zone = DEFAULT_ZONE;
        Optional<UserSettings> settings = userSettingsRepo.findById(userId);
        if (settings.isPresent()) {
            String configured = settings.get().getTimezone();
            if (configured != null && !configured.isBlank()) {
                try {
                    zone = ZoneId.of(configured);
                } catch (java.time.DateTimeException ex) {
                    log.warn(
                            "Invalid user timezone '{}'; falling back to {}",
                            configured,
                            DEFAULT_ZONE);
                }
            }
        }
        return LocalDate.now(zone).getYear();
    }

    private TrTaxReportResponse.DividendStoppage aggregateDividendStoppage(
            List<UUID> portfolioIds, LocalDate from, LocalDate to) {
        Object[] totals =
                dividendRepo.sumStoppageTotalsByPortfoliosAndRange(portfolioIds, from, to);
        BigDecimal totalGross = nullToZero(extractBigDecimal(totals, 0));
        BigDecimal totalWithholding = nullToZero(extractBigDecimal(totals, 1));
        BigDecimal totalNet = nullToZero(extractBigDecimal(totals, 2));

        List<Object[]> perAsset =
                dividendRepo.sumStoppageByAssetAndPortfoliosAndRange(portfolioIds, from, to);
        Map<UUID, Asset> assetCache = new HashMap<>();
        List<TrTaxReportResponse.AssetStoppage> byAsset = new ArrayList<>(perAsset.size());
        for (Object[] row : perAsset) {
            UUID assetId = (UUID) row[0];
            BigDecimal gross = nullToZero(extractBigDecimal(row, 1));
            BigDecimal withholding = nullToZero(extractBigDecimal(row, 2));
            BigDecimal net = nullToZero(extractBigDecimal(row, 3));
            Asset asset =
                    assetCache.computeIfAbsent(assetId, id -> assetRepo.findById(id).orElse(null));
            byAsset.add(
                    new TrTaxReportResponse.AssetStoppage(
                            assetId,
                            asset != null ? asset.getSymbol() : null,
                            asset != null ? asset.getName() : null,
                            gross,
                            withholding,
                            net));
        }

        return new TrTaxReportResponse.DividendStoppage(
                totalGross, totalWithholding, totalNet, byAsset);
    }

    private BigDecimal computeRealizedScopedToAssetTypes(
            UUID userId, int year, TrTaxParameters params) {
        CapitalGainsResponse response = capitalGainsService.compute(userId, year);
        if (params == null || params.appliesTo() == null || params.appliesTo().isEmpty()) {
            return nullToZero(response.realizedGain());
        }
        Set<String> appliesTo = new HashSet<>(params.appliesTo());
        Map<UUID, Asset> assetCache = new HashMap<>();
        BigDecimal realized = BigDecimal.ZERO;
        for (CapitalGainsResponse.Event event : response.events()) {
            Asset asset =
                    assetCache.computeIfAbsent(
                            event.assetId(), id -> assetRepo.findById(id).orElse(null));
            if (asset == null || asset.getAssetType() == null) continue;
            if (!appliesTo.contains(asset.getAssetType().name())) continue;
            realized = realized.add(nullToZero(event.realizedGain()));
        }
        return realized;
    }

    private TrTaxReportResponse.CapitalGainsThreshold buildThreshold(
            BigDecimal realizedTry, TrTaxParameters params) {
        if (params == null || params.capitalGainsThresholdTry() == null) {
            return new TrTaxReportResponse.CapitalGainsThreshold(
                    realizedTry, null, null, null, STATUS_UNKNOWN);
        }
        BigDecimal threshold = params.capitalGainsThresholdTry();
        BigDecimal headroom = threshold.subtract(realizedTry);
        BigDecimal usedRatio =
                threshold.signum() == 0
                        ? BigDecimal.ZERO
                        : realizedTry.divide(threshold, 4, RoundingMode.HALF_UP);
        String status;
        if (usedRatio.compareTo(BigDecimal.ONE) > 0) {
            status = STATUS_OVER;
        } else if (usedRatio.compareTo(APPROACHING_RATIO) >= 0) {
            status = STATUS_APPROACHING;
        } else {
            status = STATUS_UNDER;
        }
        return new TrTaxReportResponse.CapitalGainsThreshold(
                realizedTry, threshold, headroom, usedRatio, status);
    }

    private List<String> buildWarnings(String status, boolean parametersMissing) {
        List<String> warnings = new ArrayList<>();
        if (parametersMissing) warnings.add(WARN_PARAMS_MISSING);
        if (STATUS_APPROACHING.equals(status)) warnings.add(WARN_APPROACHING);
        if (STATUS_OVER.equals(status)) warnings.add(WARN_EXCEEDED);
        return warnings;
    }

    private TrTaxReportResponse zeroFilledResponse(int year, TrTaxParameters params) {
        TrTaxReportResponse.DividendStoppage stoppage =
                new TrTaxReportResponse.DividendStoppage(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        TrTaxReportResponse.CapitalGainsThreshold capitalGains =
                buildThreshold(BigDecimal.ZERO, params);
        List<String> warnings = buildWarnings(capitalGains.status(), params == null);
        return new TrTaxReportResponse(year, params, stoppage, capitalGains, warnings);
    }

    private static BigDecimal extractBigDecimal(Object[] row, int idx) {
        if (row == null || idx >= row.length) return null;
        Object cell = row[idx];
        if (cell == null) return null;
        if (cell instanceof BigDecimal bd) return bd;
        if (cell instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(cell.toString());
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
