package com.fintrack.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Direct unit tests for {@link ProductionProfileGuard#enforce()}. Each test wires a {@link
 * MockEnvironment} with the relevant {@code spring.*} / {@code jwt.*} / {@code fintrack.*}
 * properties and a {@link CorsProperties} instance, then invokes {@code enforce()} directly. This
 * keeps the boot path off the Spring Boot context-load critical path while still exercising every
 * branch of the guard.
 */
class ProductionProfileGuardTest {

    private static final String VALID_JWT_SECRET =
            "production-jwt-token-256-bits-of-truly-random-bytes-okay-here";
    private static final String VALID_RECEIPT_SECRET =
            "production-receipt-token-32-plus-bytes-of-random-data-here";
    private static final String VALID_REDIS_TOKEN = "redis-prod-token-value";
    private static final String VALID_WEBAUTHN_RPID = "fintrack.example.com";
    private static final String VALID_WEBAUTHN_ORIGIN = "https://fintrack.example.com";
    private static final String VALID_SENTRY_DSN = "https://abc@glitchtip.local/1";
    private static final List<String> VALID_ORIGINS =
            List.of("https://fintrack.example.com", "https://api.fintrack.example.com");

    @Test
    void allRequiredVarsSet_passes() {
        ProductionProfileGuard guard =
                new ProductionProfileGuard(validEnvironment(), new CorsProperties(VALID_ORIGINS));

        // No exception means the configuration was accepted.
        guard.enforce();
    }

    @Test
    void missingCorsAllowedOrigins_throwsMentioningCorsAllowedOrigins() {
        ProductionProfileGuard guard =
                new ProductionProfileGuard(validEnvironment(), new CorsProperties(List.of()));

        assertThatThrownBy(guard::enforce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void wildcardCorsOrigin_isRejected() {
        ProductionProfileGuard guard =
                new ProductionProfileGuard(validEnvironment(), new CorsProperties(List.of("*")));

        assertThatThrownBy(guard::enforce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS")
                .hasMessageContaining("wildcard");
    }

    @Test
    void defaultJwtSecret_isRejected() {
        MockEnvironment env = validEnvironment();
        env.setProperty("jwt.secret", ProductionProfileGuard.DEFAULT_JWT_SECRET);
        ProductionProfileGuard guard = new ProductionProfileGuard(env, validCorsProperties());

        assertThatThrownBy(guard::enforce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void defaultReceiptSigningSecret_isRejected() {
        MockEnvironment env = validEnvironment();
        env.setProperty(
                "fintrack.receipt.signing-secret",
                ProductionProfileGuard.DEFAULT_RECEIPT_SIGNING_SECRET);
        ProductionProfileGuard guard = new ProductionProfileGuard(env, validCorsProperties());

        assertThatThrownBy(guard::enforce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECEIPT_SIGNING_SECRET");
    }

    @Test
    void enforce_failsWhenSentryDsnBlank() {
        MockEnvironment env = validEnvironment();
        env.setProperty("sentry.dsn", "");
        ProductionProfileGuard guard = new ProductionProfileGuard(env, validCorsProperties());

        assertThatThrownBy(guard::enforce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SENTRY_DSN");
    }

    @Test
    void enforce_passesWithAllProductionEnvSet() {
        ProductionProfileGuard guard =
                new ProductionProfileGuard(validEnvironment(), validCorsProperties());

        // No exception means the configuration was accepted with every required
        // env (including the new SENTRY_DSN check) populated.
        guard.enforce();
    }

    @Test
    void multipleViolations_areAggregatedOnSeparateLines() {
        MockEnvironment env = validEnvironment();
        env.setProperty("jwt.secret", ProductionProfileGuard.DEFAULT_JWT_SECRET);
        env.setProperty(
                "fintrack.receipt.signing-secret",
                ProductionProfileGuard.DEFAULT_RECEIPT_SIGNING_SECRET);
        env.setProperty("spring.data.redis.password", "");
        env.setProperty("fintrack.webauthn.rpId", ProductionProfileGuard.DEFAULT_WEBAUTHN_RPID);
        env.setProperty("fintrack.webauthn.origin", ProductionProfileGuard.DEFAULT_WEBAUTHN_ORIGIN);
        ProductionProfileGuard guard =
                new ProductionProfileGuard(env, new CorsProperties(List.of()));

        Throwable thrown = catchThrowable(guard::enforce);

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        String message = thrown.getMessage();
        assertThat(message).contains("CORS_ALLOWED_ORIGINS");
        assertThat(message).contains("SPRING_REDIS_PASSWORD");
        assertThat(message).contains("JWT_SECRET");
        assertThat(message).contains("RECEIPT_SIGNING_SECRET");
        assertThat(message).contains("WEBAUTHN_RPID");
        assertThat(message).contains("WEBAUTHN_ORIGIN");
        // Each violation lives on its own line.
        assertThat(message.split("\n")).hasSizeGreaterThanOrEqualTo(6);
    }

    private static MockEnvironment validEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.data.redis.password", VALID_REDIS_TOKEN);
        env.setProperty("jwt.secret", VALID_JWT_SECRET);
        env.setProperty("fintrack.receipt.signing-secret", VALID_RECEIPT_SECRET);
        env.setProperty("fintrack.webauthn.rpId", VALID_WEBAUTHN_RPID);
        env.setProperty("fintrack.webauthn.origin", VALID_WEBAUTHN_ORIGIN);
        env.setProperty("sentry.dsn", VALID_SENTRY_DSN);
        return env;
    }

    private static CorsProperties validCorsProperties() {
        return new CorsProperties(VALID_ORIGINS);
    }
}
