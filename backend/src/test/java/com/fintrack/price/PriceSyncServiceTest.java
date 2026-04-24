package com.fintrack.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.PriceHistory;
import com.fintrack.price.client.CoinGeckoClient;
import com.fintrack.price.client.ExchangeRateClient;
import com.fintrack.price.client.PreciousMetalsClient;
import com.fintrack.price.client.TefasClient;
import com.fintrack.price.client.YahooFinanceClient;
import com.fintrack.price.client.YahooFinanceClient.Quote;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceSyncServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock CoinGeckoClient coinGeckoClient;
    @Mock ExchangeRateClient exchangeRateClient;
    @Mock TefasClient tefasClient;
    @Mock PreciousMetalsClient preciousMetalsClient;
    @Mock YahooFinanceClient yahooFinanceClient;

    @InjectMocks PriceSyncService service;

    private Asset asset(AssetType type, String symbol, Map<String, Object> metadata) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol)
                .assetType(type)
                .currency("TRY")
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> meta(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void refreshCryptoSkipsAssetsWithoutCoingeckoId() {
        Asset noId = asset(AssetType.CRYPTO, "BTC", new HashMap<>());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CRYPTO))
                .thenReturn(List.of(noId));

        assertThat(service.refreshCrypto()).isZero();
        verify(coinGeckoClient, never()).fetchPrices(any());
    }

    @Test
    void refreshCryptoUpdatesPriceAndPriceUsdAndRecordsHistory() {
        Asset btc = asset(AssetType.CRYPTO, "BTC", meta("coingeckoId", "bitcoin"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CRYPTO))
                .thenReturn(List.of(btc));
        when(coinGeckoClient.fetchPrices(any()))
                .thenReturn(
                        Map.of(
                                "bitcoin",
                                new CoinGeckoClient.PricePair(
                                        new BigDecimal("3000000"), new BigDecimal("95000"))));

        int count = service.refreshCrypto();

        assertThat(count).isEqualTo(1);
        assertThat(btc.getPrice()).isEqualByComparingTo("3000000");
        assertThat(btc.getPriceUsd()).isEqualByComparingTo("95000");
        assertThat(btc.getPriceUpdatedAt()).isNotNull();
        verify(priceHistoryRepository).save(any(PriceHistory.class));
    }

    @Test
    void refreshCurrenciesUpdatesFromRates() {
        Asset usd = asset(AssetType.CURRENCY, "USD", meta("exchangeCode", "USD"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CURRENCY))
                .thenReturn(List.of(usd));
        when(exchangeRateClient.fetchTryRates(any()))
                .thenReturn(Map.of("USD", new BigDecimal("34.5")));

        int count = service.refreshCurrencies();

        assertThat(count).isEqualTo(1);
        assertThat(usd.getPrice()).isEqualByComparingTo("34.5");
    }

    @Test
    void refreshCurrenciesNoOpWhenNoBases() {
        Asset noCode = asset(AssetType.CURRENCY, "NA", new HashMap<>());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CURRENCY))
                .thenReturn(List.of(noCode));

        assertThat(service.refreshCurrencies()).isZero();
        verify(exchangeRateClient, never()).fetchTryRates(any());
    }

    @Test
    void refreshMetalsConvertsOunceToTryUsingFxRate() {
        Asset gold = asset(AssetType.GOLD, "XAU", meta("metalsSymbol", "XAU"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD))
                .thenReturn(List.of(gold));
        when(preciousMetalsClient.fetchUsdPerOunce(any()))
                .thenReturn(Map.of("XAU", new BigDecimal("2000")));
        when(exchangeRateClient.fetchTryRates(any()))
                .thenReturn(Map.of("USD", new BigDecimal("30")));

        int count = service.refreshMetals();

        assertThat(count).isEqualTo(1);
        assertThat(gold.getPrice()).isEqualByComparingTo("60000.0000");
        assertThat(gold.getPriceUsd()).isEqualByComparingTo("2000.0000");
    }

    @Test
    void refreshMetalsHandlesGramUnitConversion() {
        Asset goldGram =
                asset(AssetType.GOLD, "XAU-g", meta("metalsSymbol", "XAU", "metalsUnit", "gram"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD))
                .thenReturn(List.of(goldGram));
        when(preciousMetalsClient.fetchUsdPerOunce(any()))
                .thenReturn(Map.of("XAU", new BigDecimal("2000")));
        when(exchangeRateClient.fetchTryRates(any()))
                .thenReturn(Map.of("USD", new BigDecimal("30")));

        service.refreshMetals();

        // 2000 USD/oz / 31.1034768 g/oz * 30 TRY/USD ~ 1929.09 TRY per gram
        assertThat(goldGram.getPrice())
                .isCloseTo(
                        new BigDecimal("1929.09"),
                        org.assertj.core.api.Assertions.within(new BigDecimal("0.5")));
    }

    @Test
    void refreshMetalsSkipsWhenUsdTryUnavailable() {
        Asset gold = asset(AssetType.GOLD, "XAU", meta("metalsSymbol", "XAU"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD))
                .thenReturn(List.of(gold));
        when(preciousMetalsClient.fetchUsdPerOunce(any()))
                .thenReturn(Map.of("XAU", new BigDecimal("2000")));
        when(exchangeRateClient.fetchTryRates(any())).thenReturn(Map.of());

        int count = service.refreshMetals();

        assertThat(count).isZero();
        assertThat(gold.getPrice()).isNull();
    }

    @Test
    void refreshStocksTryCurrencyAppliesPriceDirectly() {
        Asset thy = asset(AssetType.STOCK, "THYAO", meta("yahooSymbol", "THYAO.IS"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.STOCK))
                .thenReturn(List.of(thy));
        when(yahooFinanceClient.fetchQuotes(any()))
                .thenReturn(Map.of("THYAO.IS", new Quote(new BigDecimal("300.5"), "TRY")));

        int count = service.refreshStocks();

        assertThat(count).isEqualTo(1);
        assertThat(thy.getPrice()).isEqualByComparingTo("300.5");
        assertThat(thy.getPriceUsd()).isNull();
    }

    @Test
    void refreshStocksConvertsUsdToTryAndStoresUsd() {
        Asset spy = asset(AssetType.STOCK, "SPY", meta("yahooSymbol", "SPY"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.STOCK))
                .thenReturn(List.of(spy));
        when(yahooFinanceClient.fetchQuotes(any()))
                .thenReturn(Map.of("SPY", new Quote(new BigDecimal("500"), "USD")));
        when(exchangeRateClient.fetchTryRates(any()))
                .thenReturn(Map.of("USD", new BigDecimal("30")));

        service.refreshStocks();

        assertThat(spy.getPrice()).isEqualByComparingTo("15000.0000");
        assertThat(spy.getPriceUsd()).isEqualByComparingTo("500");
    }

    @Test
    void refreshStocksSkipsUsdStockWhenFxUnavailable() {
        Asset spy = asset(AssetType.STOCK, "SPY", meta("yahooSymbol", "SPY"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.STOCK))
                .thenReturn(List.of(spy));
        when(yahooFinanceClient.fetchQuotes(any()))
                .thenReturn(Map.of("SPY", new Quote(new BigDecimal("500"), "USD")));
        when(exchangeRateClient.fetchTryRates(any())).thenReturn(Map.of());

        int count = service.refreshStocks();

        assertThat(count).isZero();
        assertThat(spy.getPrice()).isNull();
    }

    @Test
    void refreshAssetReturnsFalseForUnknownId() {
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.refreshAsset(id)).isFalse();
    }

    @Test
    void refreshAssetCryptoPathUpdatesPrice() {
        Asset btc = asset(AssetType.CRYPTO, "BTC", meta("coingeckoId", "bitcoin"));
        when(assetRepository.findById(btc.getId())).thenReturn(Optional.of(btc));
        when(coinGeckoClient.fetchPrices(any()))
                .thenReturn(
                        Map.of(
                                "bitcoin",
                                new CoinGeckoClient.PricePair(
                                        new BigDecimal("3000000"), new BigDecimal("95000"))));

        assertThat(service.refreshAsset(btc.getId())).isTrue();
        assertThat(btc.getPrice()).isEqualByComparingTo("3000000");
    }

    @Test
    void refreshAssetCryptoWithoutMetadataReturnsFalse() {
        Asset btc = asset(AssetType.CRYPTO, "BTC", new HashMap<>());
        when(assetRepository.findById(btc.getId())).thenReturn(Optional.of(btc));

        assertThat(service.refreshAsset(btc.getId())).isFalse();
    }

    @Test
    void refreshAssetFundPathPicksEmkTypeWhenMetadataSaysEmk() {
        Asset fund = asset(AssetType.FUND, "BES1", meta("tefasCode", "BES1", "tefasType", "EMK"));
        when(assetRepository.findById(fund.getId())).thenReturn(Optional.of(fund));
        when(tefasClient.fetchPrice("BES1", TefasClient.FundType.EMK))
                .thenReturn(new BigDecimal("2.50"));

        assertThat(service.refreshAsset(fund.getId())).isTrue();
        assertThat(fund.getPrice()).isEqualByComparingTo("2.50");
    }

    @Test
    void refreshAssetFundReturnsFalseWhenPriceZero() {
        Asset fund = asset(AssetType.FUND, "XX", meta("tefasCode", "XX"));
        when(assetRepository.findById(fund.getId())).thenReturn(Optional.of(fund));
        when(tefasClient.fetchPrice(eqAny(), eqAny2())).thenReturn(BigDecimal.ZERO);

        assertThat(service.refreshAsset(fund.getId())).isFalse();
    }

    @Test
    void refreshAllAggregatesPerProviderCounts() {
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(any())).thenReturn(List.of());

        PriceSyncService.SyncResult res = service.refreshAll();

        assertThat(res.cryptoUpdated()).isZero();
        assertThat(res.currencyUpdated()).isZero();
        assertThat(res.fundUpdated()).isZero();
        assertThat(res.metalUpdated()).isZero();
        assertThat(res.stockUpdated()).isZero();
        assertThat(res.runAt()).isNotNull();
    }

    @Test
    void refreshLiveSkipsFundRefresh() {
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(any())).thenReturn(List.of());

        PriceSyncService.SyncResult res = service.refreshLive();

        assertThat(res.fundUpdated()).isZero();
        verify(tefasClient, never()).fetchPrice(any(), any());
    }

    @Test
    void knownCryptoIdsCollectsIdsFromMetadata() {
        Asset btc = asset(AssetType.CRYPTO, "BTC", meta("coingeckoId", "bitcoin"));
        Asset eth = asset(AssetType.CRYPTO, "ETH", meta("coingeckoId", "ethereum"));
        Asset noId = asset(AssetType.CRYPTO, "FOO", new HashMap<>());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CRYPTO))
                .thenReturn(List.of(btc, eth, noId));

        assertThat(service.knownCryptoIds()).containsExactlyInAnyOrder("bitcoin", "ethereum");
    }

    @Test
    void refreshCurrencyPathWritesHistory() {
        Asset usd = asset(AssetType.CURRENCY, "USD", meta("exchangeCode", "USD"));
        when(assetRepository.findById(usd.getId())).thenReturn(Optional.of(usd));
        when(exchangeRateClient.fetchTryRates(any()))
                .thenReturn(Map.of("USD", new BigDecimal("34.5")));

        assertThat(service.refreshAsset(usd.getId())).isTrue();
        ArgumentCaptor<PriceHistory> captor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAssetId()).isEqualTo(usd.getId());
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("34.5");
    }

    private static String eqAny() {
        return org.mockito.ArgumentMatchers.any(String.class);
    }

    private static TefasClient.FundType eqAny2() {
        return org.mockito.ArgumentMatchers.any(TefasClient.FundType.class);
    }
}
