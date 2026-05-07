package com.fintrack.report.tax.tr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrTaxServiceTest {

    @Mock TrTaxParametersLoader parametersLoader;
    @Mock CapitalGainsService capitalGainsService;
    @Mock DividendRepository dividendRepo;
    @Mock PortfolioRepository portfolioRepo;
    @Mock AssetRepository assetRepo;
    @Mock UserSettingsRepository userSettingsRepo;

    @InjectMocks TrTaxService service;

    private UUID userId;
    private UUID portfolioId;
    private TrTaxParameters params2025;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        portfolioId = UUID.randomUUID();
        params2025 =
                new TrTaxParameters(
                        2025,
                        new BigDecimal("158000"),
                        List.of("STOCK"),
                        new BigDecimal("0.15"),
                        new BigDecimal("0.10"),
                        "test",
                        "test");

        Portfolio p = Portfolio.builder().id(portfolioId).userId(userId).name("Main").build();
        lenient()
                .when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p));
        lenient().when(parametersLoader.findByYear(2025)).thenReturn(Optional.of(params2025));
        lenient()
                .when(dividendRepo.sumStoppageTotalsByPortfoliosAndRange(anyList(), any(), any()))
                .thenReturn(zeroTotalsRow());
        lenient()
                .when(dividendRepo.sumStoppageByAssetAndPortfoliosAndRange(anyList(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void under_whenRealizedBelow80Percent() {
        stubRealizedAsSingleStockEvent(new BigDecimal("100000"));

        TrTaxReportResponse response = service.compute(userId, 2025);

        assertThat(response.capitalGains().status()).isEqualTo("under");
        assertThat(response.capitalGains().realizedTry()).isEqualByComparingTo("100000");
        assertThat(response.capitalGains().headroomTry()).isEqualByComparingTo("58000");
        assertThat(response.capitalGains().usedRatio()).isEqualByComparingTo("0.6329");
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void approaching_whenRealizedBetween80And100Percent() {
        stubRealizedAsSingleStockEvent(new BigDecimal("130000"));

        TrTaxReportResponse response = service.compute(userId, 2025);

        assertThat(response.capitalGains().status()).isEqualTo("approaching");
        assertThat(response.warnings()).contains("approaching-threshold");
    }

    @Test
    void over_whenRealizedExceedsThreshold() {
        stubRealizedAsSingleStockEvent(new BigDecimal("200000"));

        TrTaxReportResponse response = service.compute(userId, 2025);

        assertThat(response.capitalGains().status()).isEqualTo("over");
        assertThat(response.capitalGains().headroomTry()).isEqualByComparingTo("-42000");
        assertThat(response.warnings()).contains("threshold-exceeded");
    }

    @Test
    void parametersMissing_returnsUnknownStatusAndWarning() {
        when(parametersLoader.findByYear(2025)).thenReturn(Optional.empty());
        stubRealizedAsSingleStockEvent(new BigDecimal("50000"));

        TrTaxReportResponse response = service.compute(userId, 2025);

        assertThat(response.parameters()).isNull();
        assertThat(response.capitalGains().thresholdTry()).isNull();
        assertThat(response.capitalGains().headroomTry()).isNull();
        assertThat(response.capitalGains().usedRatio()).isNull();
        assertThat(response.capitalGains().status()).isEqualTo("unknown");
        assertThat(response.capitalGains().realizedTry()).isEqualByComparingTo("50000");
        assertThat(response.warnings()).contains("parameters-missing");
    }

    @Test
    void dividendStoppage_aggregatesGrossWithholdingNet() {
        when(dividendRepo.sumStoppageTotalsByPortfoliosAndRange(anyList(), any(), any()))
                .thenReturn(
                        new Object[] {
                            new BigDecimal("10000"), new BigDecimal("1500"), new BigDecimal("8500")
                        });
        stubRealizedAsSingleStockEvent(BigDecimal.ZERO);

        TrTaxReportResponse response = service.compute(userId, 2025);

        assertThat(response.dividendStoppage().totalGrossTry()).isEqualByComparingTo("10000");
        assertThat(response.dividendStoppage().totalWithholdingTry()).isEqualByComparingTo("1500");
        assertThat(response.dividendStoppage().totalNetTry()).isEqualByComparingTo("8500");
    }

    @Test
    void dividendStoppage_byAssetIsPopulated() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        Asset asset1 = Asset.builder().id(a1).symbol("THYAO").name("Turk Hava Yollari").build();
        Asset asset2 = Asset.builder().id(a2).symbol("AKBNK").name("Akbank").build();
        when(dividendRepo.sumStoppageByAssetAndPortfoliosAndRange(anyList(), any(), any()))
                .thenReturn(
                        List.of(
                                new Object[] {
                                    a1,
                                    new BigDecimal("4000"),
                                    new BigDecimal("600"),
                                    new BigDecimal("3400")
                                },
                                new Object[] {
                                    a2,
                                    new BigDecimal("6000"),
                                    new BigDecimal("900"),
                                    new BigDecimal("5100")
                                }));
        when(assetRepo.findById(a1)).thenReturn(Optional.of(asset1));
        when(assetRepo.findById(a2)).thenReturn(Optional.of(asset2));
        stubRealizedAsSingleStockEvent(BigDecimal.ZERO);

        TrTaxReportResponse response = service.compute(userId, 2025);

        List<TrTaxReportResponse.AssetStoppage> rows = response.dividendStoppage().byAsset();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).assetSymbol()).isEqualTo("THYAO");
        assertThat(rows.get(0).assetName()).isEqualTo("Turk Hava Yollari");
        assertThat(rows.get(0).grossTry()).isEqualByComparingTo("4000");
        assertThat(rows.get(1).assetSymbol()).isEqualTo("AKBNK");
        assertThat(rows.get(1).withholdingTry()).isEqualByComparingTo("900");
    }

    @Test
    void multiPortfolio_aggregatesAcrossAllOwnedPortfolios() {
        UUID secondPortfolioId = UUID.randomUUID();
        Portfolio p1 = Portfolio.builder().id(portfolioId).userId(userId).name("Main").build();
        Portfolio p2 = Portfolio.builder().id(secondPortfolioId).userId(userId).name("BES").build();
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p1, p2));
        stubRealizedAsSingleStockEvent(BigDecimal.ZERO);

        service.compute(userId, 2025);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dividendRepo)
                .sumStoppageTotalsByPortfoliosAndRange(idsCaptor.capture(), any(), any());
        assertThat(idsCaptor.getValue()).containsExactly(portfolioId, secondPortfolioId);
    }

    @Test
    void noPortfolios_returnsZeroFilledResponse() {
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        TrTaxReportResponse response = service.compute(userId, 2025);

        assertThat(response.dividendStoppage().totalGrossTry()).isEqualByComparingTo("0");
        assertThat(response.dividendStoppage().totalWithholdingTry()).isEqualByComparingTo("0");
        assertThat(response.dividendStoppage().totalNetTry()).isEqualByComparingTo("0");
        assertThat(response.dividendStoppage().byAsset()).isEmpty();
        assertThat(response.capitalGains().realizedTry()).isEqualByComparingTo("0");
        assertThat(response.capitalGains().status()).isEqualTo("under");
        verifyNoInteractions(dividendRepo);
        verifyNoInteractions(capitalGainsService);
    }

    @Test
    void yearDefaultsFromUserTimezone() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(
                        Optional.of(UserSettings.builder().userId(userId).timezone("UTC").build()));
        int expected = LocalDate.now(ZoneId.of("UTC")).getYear();
        when(parametersLoader.findByYear(expected)).thenReturn(Optional.of(params2025));
        stubRealizedAsSingleStockEventForYear(expected, BigDecimal.ZERO);

        TrTaxReportResponse response = service.compute(userId, null);

        assertThat(response.year()).isEqualTo(expected);
    }

    @Test
    void yearDefaultsToIstanbulWhenSettingsMissing() {
        when(userSettingsRepo.findById(userId)).thenReturn(Optional.empty());
        int expected = LocalDate.now(ZoneId.of("Europe/Istanbul")).getYear();
        when(parametersLoader.findByYear(expected)).thenReturn(Optional.of(params2025));
        stubRealizedAsSingleStockEventForYear(expected, BigDecimal.ZERO);

        TrTaxReportResponse response = service.compute(userId, null);

        assertThat(response.year()).isEqualTo(expected);
    }

    @Test
    void appliesTo_filtersCapitalGainsEventsByAssetType() {
        UUID stockAssetId = UUID.randomUUID();
        UUID cryptoAssetId = UUID.randomUUID();
        UUID fundAssetId = UUID.randomUUID();
        Asset stock =
                Asset.builder()
                        .id(stockAssetId)
                        .symbol("THYAO")
                        .name("Turk Hava Yollari")
                        .assetType(Asset.AssetType.STOCK)
                        .build();
        Asset crypto =
                Asset.builder()
                        .id(cryptoAssetId)
                        .symbol("BTC")
                        .name("Bitcoin")
                        .assetType(Asset.AssetType.CRYPTO)
                        .build();
        Asset fund =
                Asset.builder()
                        .id(fundAssetId)
                        .symbol("AFA")
                        .name("Ata Fund")
                        .assetType(Asset.AssetType.FUND)
                        .build();
        when(assetRepo.findById(stockAssetId)).thenReturn(Optional.of(stock));
        when(assetRepo.findById(cryptoAssetId)).thenReturn(Optional.of(crypto));
        when(assetRepo.findById(fundAssetId)).thenReturn(Optional.of(fund));
        when(capitalGainsService.compute(eq(userId), eq(2025)))
                .thenReturn(
                        new CapitalGainsResponse(
                                2025,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                new BigDecimal("130000"),
                                BigDecimal.ZERO,
                                List.of(),
                                List.of(
                                        event(stockAssetId, new BigDecimal("50000")),
                                        event(cryptoAssetId, new BigDecimal("70000")),
                                        event(fundAssetId, new BigDecimal("10000")))));

        TrTaxReportResponse response = service.compute(userId, 2025);

        assertThat(response.capitalGains().realizedTry()).isEqualByComparingTo("50000");
        assertThat(response.capitalGains().status()).isEqualTo("under");
    }

    private void stubRealizedAsSingleStockEvent(BigDecimal realized) {
        stubRealizedAsSingleStockEventForYear(2025, realized);
    }

    private void stubRealizedAsSingleStockEventForYear(int year, BigDecimal realized) {
        UUID assetId = UUID.randomUUID();
        Asset stock =
                Asset.builder()
                        .id(assetId)
                        .symbol("THYAO")
                        .name("Turk Hava Yollari")
                        .assetType(Asset.AssetType.STOCK)
                        .build();
        lenient().when(assetRepo.findById(assetId)).thenReturn(Optional.of(stock));
        lenient()
                .when(capitalGainsService.compute(eq(userId), eq(year)))
                .thenReturn(
                        new CapitalGainsResponse(
                                year,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                realized,
                                BigDecimal.ZERO,
                                List.of(),
                                List.of(event(assetId, realized))));
    }

    private static CapitalGainsResponse.Event event(UUID assetId, BigDecimal realizedGain) {
        return new CapitalGainsResponse.Event(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Main",
                assetId,
                "SYM",
                "Name",
                LocalDate.of(2025, 6, 1),
                BigDecimal.ONE,
                BigDecimal.ONE,
                realizedGain,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                realizedGain);
    }

    private static Object[] zeroTotalsRow() {
        return new Object[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }
}
