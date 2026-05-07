package com.fintrack.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.metrics.PriceSyncMetrics;
import com.fintrack.price.client.CoinGeckoClient;
import com.fintrack.price.client.ExchangeRateClient;
import com.fintrack.price.client.PreciousMetalsClient;
import com.fintrack.price.client.TefasClient;
import com.fintrack.price.client.YahooFinanceClient;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pins the PriceSyncMetrics wiring on the live-price refresh paths. {@link PriceSyncService} is
 * exercised under a real {@link SimpleMeterRegistry} (not a mock) so the gauge values are read off
 * the registry exactly as the Prometheus scrape would.
 */
@ExtendWith(MockitoExtension.class)
class PriceSyncServiceMetricsIntegrationTest {

    private static final String GAUGE_NAME = "fintrack.price.sync.last.success.timestamp.seconds";

    @Mock AssetRepository assetRepository;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock CoinGeckoClient coinGeckoClient;
    @Mock ExchangeRateClient exchangeRateClient;
    @Mock TefasClient tefasClient;
    @Mock PreciousMetalsClient preciousMetalsClient;
    @Mock YahooFinanceClient yahooFinanceClient;

    PriceSyncService service;
    ExecutorService executor;
    SimpleMeterRegistry registry;
    PriceSyncMetrics metrics;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        registry = new SimpleMeterRegistry();
        metrics = new PriceSyncMetrics(registry);
        metrics.registerGauges();

        PriceApiProperties props =
                new PriceApiProperties(
                        new PriceApiProperties.CoinGecko("https://example.invalid", null, true),
                        new PriceApiProperties.ExchangeRate("https://example.invalid", null, true),
                        new PriceApiProperties.Tefas("https://example.invalid", true, 4),
                        30);
        service =
                new PriceSyncService(
                        assetRepository,
                        priceHistoryRepository,
                        coinGeckoClient,
                        exchangeRateClient,
                        tefasClient,
                        preciousMetalsClient,
                        yahooFinanceClient,
                        executor,
                        props,
                        metrics);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

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

    private double gauge(String source) {
        Gauge g = registry.find(GAUGE_NAME).tag("source", source).gauge();
        assertThat(g).isNotNull();
        return g.value();
    }

    @Test
    void refreshLive_recordsSuccessForEachPopulatedSource() {
        Asset btc = asset(AssetType.CRYPTO, "BTC", meta("coingeckoId", "bitcoin"));
        Asset usd = asset(AssetType.CURRENCY, "USD", meta("exchangeCode", "USD"));
        Asset gold = asset(AssetType.GOLD, "XAU", meta("metalsSymbol", "XAU"));
        Asset spy = asset(AssetType.STOCK, "SPY", meta("yahooSymbol", "SPY.IS"));

        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CRYPTO))
                .thenReturn(List.of(btc));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CURRENCY))
                .thenReturn(List.of(usd));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD))
                .thenReturn(List.of(gold));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.STOCK))
                .thenReturn(List.of(spy));

        when(coinGeckoClient.fetchPrices(any()))
                .thenReturn(
                        Map.of(
                                "bitcoin",
                                new CoinGeckoClient.PricePair(
                                        new BigDecimal("3000000"), new BigDecimal("95000"))));
        when(exchangeRateClient.fetchTryRates(any()))
                .thenReturn(Map.of("USD", new BigDecimal("34.5")));
        when(preciousMetalsClient.fetchUsdPerOunce(any()))
                .thenReturn(Map.of("XAU", new BigDecimal("2000")));
        when(yahooFinanceClient.fetchQuotes(any()))
                .thenReturn(
                        Map.of(
                                "SPY.IS",
                                new YahooFinanceClient.Quote(new BigDecimal("300.5"), "TRY")));

        long before = Instant.now().getEpochSecond();
        service.refreshLive();
        long after = Instant.now().getEpochSecond();

        for (String src : List.of("crypto", "currency", "metal", "stock")) {
            double value = gauge(src);
            assertThat(Double.isNaN(value)).as("gauge for %s should be set", src).isFalse();
            assertThat(value).isBetween((double) (before - 1), (double) (after + 1));
        }
    }

    @Test
    void refreshLive_skipsRecordingForEmptyFetches() {
        // Crypto returns empty -> gauge stays NaN.
        Asset usd = asset(AssetType.CURRENCY, "USD", meta("exchangeCode", "USD"));
        Asset gold = asset(AssetType.GOLD, "XAU", meta("metalsSymbol", "XAU"));
        Asset spy = asset(AssetType.STOCK, "SPY", meta("yahooSymbol", "SPY.IS"));

        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CRYPTO))
                .thenReturn(List.of());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CURRENCY))
                .thenReturn(List.of(usd));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD))
                .thenReturn(List.of(gold));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.STOCK))
                .thenReturn(List.of(spy));

        // No crypto stub -- the empty asset list short-circuits before any call.
        lenient()
                .when(exchangeRateClient.fetchTryRates(any()))
                .thenReturn(Map.of("USD", new BigDecimal("34.5")));
        lenient()
                .when(preciousMetalsClient.fetchUsdPerOunce(any()))
                .thenReturn(Map.of("XAU", new BigDecimal("2000")));
        lenient()
                .when(yahooFinanceClient.fetchQuotes(any()))
                .thenReturn(
                        Map.of(
                                "SPY.IS",
                                new YahooFinanceClient.Quote(new BigDecimal("300.5"), "TRY")));

        service.refreshLive();

        assertThat(Double.isNaN(gauge("crypto"))).as("crypto gauge stays NaN").isTrue();
        assertThat(Double.isNaN(gauge("currency"))).as("currency gauge populated").isFalse();
        assertThat(Double.isNaN(gauge("metal"))).as("metal gauge populated").isFalse();
        assertThat(Double.isNaN(gauge("stock"))).as("stock gauge populated").isFalse();
    }

    @Test
    void refreshCryptoSingleSourceMethod_recordsCryptoFreshness() {
        Asset btc = asset(AssetType.CRYPTO, "BTC", meta("coingeckoId", "bitcoin"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.CRYPTO))
                .thenReturn(List.of(btc));
        when(coinGeckoClient.fetchPrices(any()))
                .thenReturn(
                        Map.of(
                                "bitcoin",
                                new CoinGeckoClient.PricePair(
                                        new BigDecimal("3000000"), new BigDecimal("95000"))));

        long before = Instant.now().getEpochSecond();
        int written = service.refreshCrypto();
        long after = Instant.now().getEpochSecond();

        assertThat(written).isEqualTo(1);
        double value = gauge("crypto");
        assertThat(Double.isNaN(value)).isFalse();
        assertThat(value).isBetween((double) (before - 1), (double) (after + 1));
        assertThat(Double.isNaN(gauge("currency"))).isTrue();
        assertThat(Double.isNaN(gauge("metal"))).isTrue();
        assertThat(Double.isNaN(gauge("stock"))).isTrue();
    }
}
