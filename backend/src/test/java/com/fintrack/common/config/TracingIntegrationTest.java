package com.fintrack.common.config;

import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.price.PriceApiProperties;
import com.fintrack.price.PriceConfig;
import com.fintrack.price.PriceHistoryRepository;
import com.fintrack.price.PriceSyncService;
import com.fintrack.price.client.CoinGeckoClient;
import com.fintrack.price.client.ExchangeRateClient;
import com.fintrack.price.client.PreciousMetalsClient;
import com.fintrack.price.client.TefasClient;
import com.fintrack.price.client.YahooFinanceClient;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * End-to-end smoke for the Phase 26 plan 01 trace pipeline at the orchestrator boundary. Drives
 * {@link PriceSyncService#refreshLive()} (which fans out four fetches on the wrapped {@code
 * tracingPriceVirtualExecutor}) and asserts the {@code price.refresh.live} observation landed in
 * the {@link TestObservationRegistry}. The wrapped executor's job is to keep observation context
 * attached across the {@link java.util.concurrent.CompletableFuture#supplyAsync} hand-off; if the
 * wrapper regresses, the observation never opens because the {@code @Observed} aspect ran on the
 * parent thread but the aspect-driven scope dies before the worker tasks run.
 */
@SpringJUnitConfig(TracingIntegrationTest.TestConfig.class)
@Import({TracingConfig.class, PriceConfig.class})
@TestPropertySource(
        properties = {
            "price-api.coingecko.base-url=https://example.invalid",
            "price-api.coingecko.enabled=true",
            "price-api.exchange-rate.base-url=https://example.invalid",
            "price-api.exchange-rate.enabled=true",
            "price-api.tefas.base-url=https://example.invalid",
            "price-api.tefas.enabled=true",
            "price-api.tefas.parallelism=4",
            "price-api.sync-interval-seconds=30"
        })
class TracingIntegrationTest {

    @MockBean AssetRepository assetRepository;
    @MockBean PriceHistoryRepository priceHistoryRepository;
    @MockBean CoinGeckoClient coinGeckoClient;
    @MockBean ExchangeRateClient exchangeRateClient;
    @MockBean TefasClient tefasClient;
    @MockBean PreciousMetalsClient preciousMetalsClient;
    @MockBean YahooFinanceClient yahooFinanceClient;

    @Autowired PriceSyncService priceSyncService;
    @Autowired ObservationRegistry observationRegistry;

    private TestObservationRegistry testRegistry() {
        return (TestObservationRegistry) observationRegistry;
    }

    @BeforeEach
    void stubEmptyAssetLookups() {
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CRYPTO))
                .thenReturn(List.of());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CURRENCY))
                .thenReturn(List.of());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.GOLD))
                .thenReturn(List.of());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.STOCK))
                .thenReturn(List.of());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.FUND))
                .thenReturn(List.of());
    }

    @Test
    void refreshLiveOpensObservationWithStableName() {
        priceSyncService.refreshLive();

        TestObservationRegistryAssert.assertThat(testRegistry())
                .hasObservationWithNameEqualTo("price.refresh.live")
                .that()
                .hasBeenStarted()
                .hasBeenStopped();
    }

    @Test
    void refreshFundsOpensObservationWithStableName() {
        priceSyncService.refreshFunds();

        TestObservationRegistryAssert.assertThat(testRegistry())
                .hasObservationWithNameEqualTo("price.refresh.funds")
                .that()
                .hasBeenStarted()
                .hasBeenStopped();
    }

    @org.springframework.context.annotation.Configuration
    @EnableAspectJAutoProxy
    @org.springframework.boot.context.properties.EnableConfigurationProperties(
            PriceApiProperties.class)
    static class TestConfig {

        @Bean
        ObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }

        @Bean
        ObservedAspect observedAspect(ObservationRegistry registry) {
            return new ObservedAspect(registry);
        }

        @Bean
        PriceSyncService priceSyncService(
                AssetRepository assetRepository,
                PriceHistoryRepository priceHistoryRepository,
                CoinGeckoClient coinGeckoClient,
                ExchangeRateClient exchangeRateClient,
                TefasClient tefasClient,
                PreciousMetalsClient preciousMetalsClient,
                YahooFinanceClient yahooFinanceClient,
                @org.springframework.beans.factory.annotation.Qualifier(
                                "tracingPriceVirtualExecutor")
                        java.util.concurrent.ExecutorService priceVirtualExecutor,
                PriceApiProperties props) {
            return new PriceSyncService(
                    assetRepository,
                    priceHistoryRepository,
                    coinGeckoClient,
                    exchangeRateClient,
                    tefasClient,
                    preciousMetalsClient,
                    yahooFinanceClient,
                    priceVirtualExecutor,
                    props,
                    null);
        }
    }
}
