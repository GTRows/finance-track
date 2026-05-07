package com.fintrack.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.metrics.PriceSyncMetrics.Source;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PriceSyncMetricsTest {

    private static final String GAUGE_NAME = "fintrack.price.sync.last.success.timestamp.seconds";

    private SimpleMeterRegistry registry;
    private PriceSyncMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PriceSyncMetrics(registry);
        metrics.registerGauges();
    }

    @Test
    void registerGauges_createsFourSourceTaggedGauges() {
        Gauge crypto = registry.find(GAUGE_NAME).tag("source", "crypto").gauge();
        Gauge currency = registry.find(GAUGE_NAME).tag("source", "currency").gauge();
        Gauge metal = registry.find(GAUGE_NAME).tag("source", "metal").gauge();
        Gauge stock = registry.find(GAUGE_NAME).tag("source", "stock").gauge();

        assertThat(crypto).isNotNull();
        assertThat(currency).isNotNull();
        assertThat(metal).isNotNull();
        assertThat(stock).isNotNull();

        Collection<Gauge> all = registry.find(GAUGE_NAME).gauges();
        assertThat(all).hasSize(4);
    }

    @Test
    void gauge_emitsNaNBeforeAnyRecord() {
        Gauge crypto = registry.find(GAUGE_NAME).tag("source", "crypto").gauge();
        assertThat(Double.isNaN(crypto.value())).isTrue();
    }

    @Test
    void recordSuccess_setsGaugeToEpochSeconds() {
        metrics.recordSuccess(Source.CRYPTO, Instant.ofEpochSecond(1_700_000_000L));

        Gauge crypto = registry.find(GAUGE_NAME).tag("source", "crypto").gauge();
        Gauge currency = registry.find(GAUGE_NAME).tag("source", "currency").gauge();

        assertThat(crypto.value()).isEqualTo(1_700_000_000.0);
        assertThat(Double.isNaN(currency.value())).isTrue();
    }

    @Test
    void recordSuccess_lastWriteWins() {
        metrics.recordSuccess(Source.STOCK, Instant.ofEpochSecond(1_700_000_000L));
        metrics.recordSuccess(Source.STOCK, Instant.ofEpochSecond(1_700_000_999L));

        Gauge stock = registry.find(GAUGE_NAME).tag("source", "stock").gauge();
        assertThat(stock.value()).isEqualTo(1_700_000_999.0);
    }
}
