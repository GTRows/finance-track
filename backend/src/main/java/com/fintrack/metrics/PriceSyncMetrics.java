package com.fintrack.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Publishes per-source price-sync freshness gauges on the same Micrometer registry that backs the
 * /actuator/prometheus endpoint. Each gauge emits {@code Double.NaN} until the first successful
 * refresh has been recorded, then emits the Unix epoch seconds of the most recent success. The
 * NaN-before-record contract avoids the cold-boot false-positive freshness alerts that a 0-default
 * would otherwise produce (a fresh process would look infinitely stale).
 *
 * <p>Funds are intentionally not in scope here: TEFAS publishes daily, so a 6h freshness SLO would
 * be either trivially passing or trivially failing depending on the calendar. Fund freshness is
 * surfaced on a sibling info panel without an alert (see Phase 26-03).
 */
@Component
@Slf4j
public class PriceSyncMetrics {

    /** Gauge name on the registry; Micrometer rewrites dots to underscores for Prometheus. */
    static final String GAUGE_NAME = "fintrack.price.sync.last.success.timestamp.seconds";

    /** Live-price sources tracked by the freshness SLI. Funds intentionally excluded. */
    public enum Source {
        CRYPTO("crypto"),
        CURRENCY("currency"),
        METAL("metal"),
        STOCK("stock");

        private final String tagValue;

        Source(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    private final MeterRegistry registry;
    private final Map<Source, AtomicReference<Instant>> lastSuccess = new EnumMap<>(Source.class);

    public PriceSyncMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (Source source : Source.values()) {
            lastSuccess.put(source, new AtomicReference<>(null));
        }
    }

    @PostConstruct
    void registerGauges() {
        for (Source source : Source.values()) {
            AtomicReference<Instant> ref = lastSuccess.get(source);
            Gauge.builder(GAUGE_NAME, ref, this::epochSecondsOrNaN)
                    .tag("source", source.tagValue())
                    .description(
                            "Wall-clock Unix epoch seconds of the most recent successful price"
                                    + " refresh, per source. NaN until first success.")
                    .register(registry);
        }
    }

    /** Records that {@code source} has completed a successful refresh at {@code at}. */
    public void recordSuccess(Source source, Instant at) {
        lastSuccess.get(source).set(at);
    }

    private double epochSecondsOrNaN(AtomicReference<Instant> ref) {
        Instant value = ref.get();
        return value == null ? Double.NaN : (double) value.getEpochSecond();
    }
}
