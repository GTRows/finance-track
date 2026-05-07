package com.fintrack.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Pins the contract of {@link ReleaseInfoConfig#releaseVersionSupplier(ReleaseInfoProperties,
 * String)}: reads the {@code version} key from the IDENTITY.yaml at the configured path on first
 * call, falls back to {@code fintrack@unknown} on missing file with a single WARN line, and
 * memoises the result so repeated calls return the same string instance.
 */
class ReleaseInfoConfigTest {

    private static final String FALLBACK = "fintrack@unknown";

    private final ReleaseInfoConfig config = new ReleaseInfoConfig();
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void attachListAppender() {
        logger = (Logger) LoggerFactory.getLogger(ReleaseInfoConfig.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void detachListAppender() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void supplierReadsVersionFromIdentityYaml() {
        Supplier<String> supplier =
                config.releaseVersionSupplier(
                        new ReleaseInfoProperties("src/test/resources/test-identity.yaml"),
                        FALLBACK);

        assertThat(supplier.get()).isEqualTo("fintrack@9.9.9");
        assertThat(warningCount()).isZero();
    }

    @Test
    void supplierFallsBackOnMissingFile() {
        Supplier<String> supplier =
                config.releaseVersionSupplier(
                        new ReleaseInfoProperties("src/test/resources/no-such-file.yaml"),
                        FALLBACK);

        assertThat(supplier.get()).isEqualTo(FALLBACK);
        // Trigger a second invocation; memoised result must not log a second warning.
        assertThat(supplier.get()).isEqualTo(FALLBACK);
        assertThat(warningCount()).isEqualTo(1);
    }

    @Test
    void supplierMemoisesAfterFirstCall() {
        Supplier<String> supplier =
                config.releaseVersionSupplier(
                        new ReleaseInfoProperties("src/test/resources/test-identity.yaml"),
                        FALLBACK);

        String first = supplier.get();
        String second = supplier.get();

        assertThat(first).isEqualTo("fintrack@9.9.9");
        assertThat(second).isSameAs(first);
    }

    private long warningCount() {
        return listAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }
}
