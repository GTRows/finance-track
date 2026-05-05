package com.fintrack.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Allow-list of origins permitted to call the API. Bound from {@code fintrack.cors.allowed-origins}
 * which itself reads {@code CORS_ALLOWED_ORIGINS} as a comma-separated list. An empty list means
 * "no explicit allow-list" — the wildcard fallback in {@link SecurityConfig} only engages outside
 * the production profile.
 */
@ConfigurationProperties(prefix = "fintrack.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null) allowedOrigins = List.of();
    }
}
