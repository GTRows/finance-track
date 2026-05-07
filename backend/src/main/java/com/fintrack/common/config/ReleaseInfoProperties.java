package com.fintrack.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

/**
 * Resolves the IDENTITY.yaml file path used by {@link ReleaseInfoConfig} to compute the Sentry
 * {@code release} tag. Default {@code IDENTITY.yaml} is the repo-root file used by {@code
 * /gtr:release}; tests override via {@code fintrack.release.identity-file}.
 */
@ConfigurationProperties(prefix = "fintrack.release")
public record ReleaseInfoProperties(@Nullable String identityFile) {
    public ReleaseInfoProperties {
        if (identityFile == null || identityFile.isBlank()) {
            identityFile = "IDENTITY.yaml";
        }
    }
}
