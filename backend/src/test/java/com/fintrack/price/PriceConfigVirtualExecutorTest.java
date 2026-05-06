package com.fintrack.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Pins the {@code priceVirtualExecutor} bean wiring: the bean is registered, runnables submitted to
 * it execute on a virtual thread, and the qualifier does not collide with the WebClient beans.
 */
@SpringJUnitConfig(PriceConfig.class)
@EnableConfigurationProperties(PriceApiProperties.class)
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
class PriceConfigVirtualExecutorTest {

    @Autowired
    @Qualifier("priceVirtualExecutor")
    ExecutorService executor;

    @Autowired
    @Qualifier("coinGeckoWebClient")
    WebClient coinGeckoWebClient;

    @Autowired
    @Qualifier("exchangeRateWebClient")
    WebClient exchangeRateWebClient;

    @Autowired
    @Qualifier("tefasWebClient")
    WebClient tefasWebClient;

    @Test
    void priceVirtualExecutorBeanIsRegistered() {
        assertThat(executor).isNotNull();
    }

    @Test
    void runnablesExecuteOnVirtualThreads() throws Exception {
        CompletableFuture<Boolean> isVirtual = new CompletableFuture<>();
        executor.submit(() -> isVirtual.complete(Thread.currentThread().isVirtual()));
        assertThat(isVirtual.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void priceVirtualExecutorIsDistinctFromWebClientBeans() {
        assertThat(coinGeckoWebClient).isNotNull();
        assertThat(exchangeRateWebClient).isNotNull();
        assertThat(tefasWebClient).isNotNull();
        assertThat((Object) executor)
                .isNotIn(coinGeckoWebClient, exchangeRateWebClient, tefasWebClient);
    }
}
