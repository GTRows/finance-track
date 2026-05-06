package com.fintrack.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.fintrack.price.client.TefasClient.FundType;
import com.fintrack.price.client.YahooFinanceClient;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pins the structural contract of {@link PriceSyncService#refreshFunds()} after the 25-03 refactor:
 * the per-fund TEFAS reads fan out on a virtual-thread executor, throttled by a {@code Semaphore}
 * keyed off {@code price-api.tefas.parallelism}; persistence happens once at the end; no {@code
 * Thread.sleep} survives anywhere on the path.
 */
@ExtendWith(MockitoExtension.class)
class PriceSyncServiceFundRefreshTest {

    @Mock AssetRepository assetRepository;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock CoinGeckoClient coinGeckoClient;
    @Mock ExchangeRateClient exchangeRateClient;
    @Mock TefasClient tefasClient;
    @Mock PreciousMetalsClient preciousMetalsClient;
    @Mock YahooFinanceClient yahooFinanceClient;

    PriceSyncService service;
    ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        service = build(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private PriceSyncService build(int parallelism) {
        PriceApiProperties props =
                new PriceApiProperties(
                        new PriceApiProperties.CoinGecko("https://example.invalid", null, true),
                        new PriceApiProperties.ExchangeRate("https://example.invalid", null, true),
                        new PriceApiProperties.Tefas("https://example.invalid", true, parallelism),
                        30);
        return new PriceSyncService(
                assetRepository,
                priceHistoryRepository,
                coinGeckoClient,
                exchangeRateClient,
                tefasClient,
                preciousMetalsClient,
                yahooFinanceClient,
                executor,
                props);
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

    @Test
    void refreshFundsWithThreeFundsCallsTefasAndPersistsAllThree() {
        Asset f1 = asset(AssetType.FUND, "F1", meta("tefasCode", "F1"));
        Asset f2 = asset(AssetType.FUND, "F2", meta("tefasCode", "F2"));
        Asset f3 = asset(AssetType.FUND, "F3", meta("tefasCode", "F3"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.FUND))
                .thenReturn(List.of(f1, f2, f3));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD)).thenReturn(List.of());
        when(tefasClient.fetchPrice("F1", FundType.YAT)).thenReturn(new BigDecimal("1.10"));
        when(tefasClient.fetchPrice("F2", FundType.YAT)).thenReturn(new BigDecimal("2.20"));
        when(tefasClient.fetchPrice("F3", FundType.YAT)).thenReturn(new BigDecimal("3.30"));

        int updated = service.refreshFunds();

        assertThat(updated).isEqualTo(3);
        assertThat(f1.getPrice()).isEqualByComparingTo("1.10");
        assertThat(f2.getPrice()).isEqualByComparingTo("2.20");
        assertThat(f3.getPrice()).isEqualByComparingTo("3.30");
        verify(priceHistoryRepository, times(3)).save(any(PriceHistory.class));
    }

    @Test
    void refreshFundsRespectsParallelismCap() throws Exception {
        // Build a service whose parallelism gate is 1 and observe that fetchPrice is never
        // reentered
        // concurrently. The mock holds the call open for a brief window so the test detects
        // overlap.
        service = build(1);

        int n = 4;
        List<Asset> funds =
                List.of(
                        asset(AssetType.FUND, "A", meta("tefasCode", "A")),
                        asset(AssetType.FUND, "B", meta("tefasCode", "B")),
                        asset(AssetType.FUND, "C", meta("tefasCode", "C")),
                        asset(AssetType.FUND, "D", meta("tefasCode", "D")));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.FUND)).thenReturn(funds);
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD)).thenReturn(List.of());

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        when(tefasClient.fetchPrice(any(), any()))
                .thenAnswer(
                        inv -> {
                            int now = inFlight.incrementAndGet();
                            maxObserved.updateAndGet(m -> Math.max(m, now));
                            try {
                                Thread.sleep(50);
                            } finally {
                                inFlight.decrementAndGet();
                            }
                            return new BigDecimal("1.00");
                        });

        int updated = service.refreshFunds();

        assertThat(updated).isEqualTo(n);
        assertThat(maxObserved.get()).isEqualTo(1);
    }

    @Test
    void refreshFundsSkipsGoldAssetsWithMetalsSymbol() {
        Asset gold =
                asset(AssetType.GOLD, "XAU", meta("metalsSymbol", "XAU", "tefasCode", "ignored"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.FUND)).thenReturn(List.of());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD))
                .thenReturn(List.of(gold));

        int updated = service.refreshFunds();

        assertThat(updated).isZero();
        verify(tefasClient, never()).fetchPrice(any(), any());
    }

    @Test
    void refreshFundsZeroPriceFromTefasIsNotPersisted() {
        Asset f = asset(AssetType.FUND, "F", meta("tefasCode", "F"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.FUND))
                .thenReturn(List.of(f));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD)).thenReturn(List.of());
        when(tefasClient.fetchPrice("F", FundType.YAT)).thenReturn(BigDecimal.ZERO);

        int updated = service.refreshFunds();

        assertThat(updated).isZero();
        assertThat(f.getPrice()).isNull();
        verify(priceHistoryRepository, never()).save(any(PriceHistory.class));
    }

    @Test
    void refreshFundsHandlesNullPriceFromTefas() {
        Asset f = asset(AssetType.FUND, "F", meta("tefasCode", "F"));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.FUND))
                .thenReturn(List.of(f));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.GOLD)).thenReturn(List.of());
        when(tefasClient.fetchPrice("F", FundType.YAT)).thenReturn(null);

        int updated = service.refreshFunds();

        assertThat(updated).isZero();
        assertThat(f.getPrice()).isNull();
    }

    @Test
    void refreshFundsSourceFileContainsNoThreadSleep() throws Exception {
        // Structural pin: this plan removed Thread.sleep from the fund refresh path. Re-introducing
        // it would silently re-instate the pre-25-03 transactional throttle.
        Path source =
                Path.of("src/main/java/com/fintrack/price/PriceSyncService.java").toAbsolutePath();
        String body = Files.readString(source);
        assertThat(body).doesNotContain("Thread.sleep");
    }
}
