package com.fintrack.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.price.PriceApiProperties;
import com.fintrack.price.PriceConfig;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.ThreadLocalAccessor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Pins the {@link TracingConfig#tracingPriceVirtualExecutor} contract: bean is wired, runs on a
 * virtual thread, propagates a context snapshot from caller to worker, and is safe to submit to
 * with no captured context. The wrapped executor must remain distinct from the raw {@code
 * priceVirtualExecutor} bean so existing consumers of the raw form keep their wiring intact.
 */
@SpringJUnitConfig({TracingConfig.class, PriceConfig.class})
@EnableConfigurationProperties(PriceApiProperties.class)
@ImportAutoConfiguration(ObservationAutoConfiguration.class)
@TestPropertySource(
        properties = {
            "price-api.coingecko.base-url=https://example.invalid",
            "price-api.coingecko.enabled=true",
            "price-api.exchange-rate.base-url=https://example.invalid",
            "price-api.exchange-rate.enabled=true",
            "price-api.tefas.base-url=https://example.invalid",
            "price-api.tefas.enabled=true",
            "price-api.tefas.parallelism=4",
            "price-api.sync-interval-seconds=30",
            "management.tracing.sampling.probability=1.0"
        })
class TracingConfigTest {

    private static final ThreadLocal<String> TEST_CONTEXT = new ThreadLocal<>();
    private static final String ACCESSOR_KEY = "tracing-config-test";

    @BeforeAll
    static void registerAccessor() {
        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(
                        new ThreadLocalAccessor<String>() {
                            @Override
                            public Object key() {
                                return ACCESSOR_KEY;
                            }

                            @Override
                            public String getValue() {
                                return TEST_CONTEXT.get();
                            }

                            @Override
                            public void setValue(String value) {
                                TEST_CONTEXT.set(value);
                            }

                            @Override
                            public void setValue() {
                                TEST_CONTEXT.remove();
                            }
                        });
    }

    @AfterAll
    static void removeAccessor() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(ACCESSOR_KEY);
        TEST_CONTEXT.remove();
    }

    @Autowired
    @Qualifier("tracingPriceVirtualExecutor")
    ExecutorService tracingExecutor;

    @Autowired
    @Qualifier("priceVirtualExecutor")
    ExecutorService rawExecutor;

    @Test
    void tracingExecutorBean_isWired() {
        assertThat(tracingExecutor).isNotNull();
        assertThat(rawExecutor).isNotNull();
        assertThat(tracingExecutor).isNotSameAs(rawExecutor);
    }

    @Test
    void tracingExecutor_runsOnVirtualThread() throws Exception {
        CompletableFuture<Boolean> isVirtual = new CompletableFuture<>();
        tracingExecutor.submit(() -> isVirtual.complete(Thread.currentThread().isVirtual()));
        assertThat(isVirtual.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void tracingExecutor_propagatesContext() throws Exception {
        TEST_CONTEXT.set("trace-abc");
        try {
            CompletableFuture<String> seen = new CompletableFuture<>();
            tracingExecutor.submit(() -> seen.complete(TEST_CONTEXT.get()));
            assertThat(seen.get(5, TimeUnit.SECONDS)).isEqualTo("trace-abc");
        } finally {
            TEST_CONTEXT.remove();
        }
    }

    @Test
    void tracingExecutor_unobservedTaskRunsCleanly() throws Exception {
        // No outer context populated; the wrapped supplier must still execute without throwing.
        TEST_CONTEXT.remove();
        ContextSnapshotFactory.builder().build().captureAll();
        CompletableFuture<String> result = new CompletableFuture<>();
        tracingExecutor.submit(() -> result.complete("done"));
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("done");
    }
}
