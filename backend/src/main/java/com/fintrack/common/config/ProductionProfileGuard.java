package com.fintrack.common.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Boot-time fail-fast for the {@code production} Spring profile. Aggregates every misconfiguration
 * into one {@link IllegalStateException} so the operator sees the full remediation list on the
 * first restart instead of one violation per restart.
 *
 * <p>Verified at startup:
 *
 * <ul>
 *   <li>{@code CORS_ALLOWED_ORIGINS} is set and contains no wildcard entry.
 *   <li>{@code SPRING_REDIS_PASSWORD} is non-blank.
 *   <li>{@code JWT_SECRET} is non-blank and not the in-tree dev default.
 *   <li>{@code RECEIPT_SIGNING_SECRET} is non-blank and not the in-tree dev default.
 *   <li>{@code WEBAUTHN_RPID} and {@code WEBAUTHN_ORIGIN} are set to non-default values.
 * </ul>
 *
 * <p>Runs before the embedded servlet container starts (via {@link PostConstruct}) so a
 * misconfigured production boot never opens a port.
 */
@Component
@Profile("production")
@RequiredArgsConstructor
public class ProductionProfileGuard {

    static final String DEFAULT_JWT_SECRET =
            "dev-secret-change-in-production-must-be-256-bits-long";
    static final String DEFAULT_RECEIPT_SIGNING_SECRET =
            "dev-receipt-signing-secret-change-in-prod";
    static final String DEFAULT_WEBAUTHN_RPID = "localhost";
    static final String DEFAULT_WEBAUTHN_ORIGIN = "http://localhost:5173";

    private final Environment environment;
    private final CorsProperties corsProperties;

    @PostConstruct
    void enforce() {
        List<String> violations = new ArrayList<>();

        List<String> origins = corsProperties.allowedOrigins();
        if (origins.isEmpty()) {
            violations.add(
                    "CORS_ALLOWED_ORIGINS: must be set to a comma-separated list of trusted"
                            + " origins (e.g. https://fintrack.example.com); wildcard '*' is"
                            + " forbidden in production.");
        } else if (origins.stream().anyMatch(o -> "*".equals(o.trim()))) {
            violations.add(
                    "CORS_ALLOWED_ORIGINS: wildcard '*' is forbidden in production; replace with"
                            + " explicit origins.");
        }

        String redisPassword = environment.getProperty("spring.data.redis.password");
        if (isBlank(redisPassword)) {
            violations.add(
                    "SPRING_REDIS_PASSWORD: must be set to a strong random password (32+ chars);"
                            + " an unauthenticated Redis is a session-store takeover risk.");
        }

        String jwtSecret = environment.getProperty("jwt.secret");
        if (isBlank(jwtSecret)) {
            violations.add(
                    "JWT_SECRET: must be set to a 256-bit random secret (generate with"
                            + " 'openssl rand -base64 64').");
        } else if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            violations.add(
                    "JWT_SECRET: still set to the in-tree dev default; rotate to a fresh 256-bit"
                            + " secret before going to production.");
        }

        String receiptSecret = environment.getProperty("fintrack.receipt.signing-secret");
        if (isBlank(receiptSecret)) {
            violations.add(
                    "RECEIPT_SIGNING_SECRET: must be set to at least 32 bytes of random data"
                            + " (generate with 'openssl rand -base64 48').");
        } else if (DEFAULT_RECEIPT_SIGNING_SECRET.equals(receiptSecret)) {
            violations.add(
                    "RECEIPT_SIGNING_SECRET: still set to the in-tree dev default; rotate to a"
                            + " fresh 32-byte secret before going to production.");
        }

        String webauthnRpId = environment.getProperty("fintrack.webauthn.rpId");
        if (isBlank(webauthnRpId)) {
            violations.add(
                    "WEBAUTHN_RPID: must be set to the deployment's registrable domain"
                            + " (e.g. fintrack.example.com).");
        } else if (DEFAULT_WEBAUTHN_RPID.equals(webauthnRpId)) {
            violations.add(
                    "WEBAUTHN_RPID: still set to the dev default 'localhost'; passkeys will not"
                            + " bind to the production origin.");
        }

        String webauthnOrigin = environment.getProperty("fintrack.webauthn.origin");
        if (isBlank(webauthnOrigin)) {
            violations.add(
                    "WEBAUTHN_ORIGIN: must be set to the production origin"
                            + " (e.g. https://fintrack.example.com).");
        } else if (DEFAULT_WEBAUTHN_ORIGIN.equals(webauthnOrigin)) {
            violations.add(
                    "WEBAUTHN_ORIGIN: still set to the dev default; passkeys will not bind to the"
                            + " production origin.");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Production profile rejected the current configuration:\n"
                            + String.join("\n", violations));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
