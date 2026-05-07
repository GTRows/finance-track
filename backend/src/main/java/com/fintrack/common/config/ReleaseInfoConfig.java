package com.fintrack.common.config;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

/**
 * Resolves the Sentry {@code release} tag at runtime by reading the project root {@code
 * IDENTITY.yaml} (the file owned by {@code /gtr:release}) and prefixing the {@code version} key
 * with {@code "fintrack@"}. The {@code FINTRACK_RELEASE_VERSION} env override (resolved by Spring's
 * placeholder before the SpEL lookup in {@code application.yml}) wins so a CI pipeline can stamp a
 * precise build identifier (e.g. {@code 1.1.0+abc1234}); when unset, this supplier returns the
 * IDENTITY.yaml-derived value. The supplier is memoised to a single read on first access so the
 * SnakeYAML parse runs once per JVM.
 */
@Slf4j
@Configuration
public class ReleaseInfoConfig {

    @Bean("releaseVersionSupplier")
    public Supplier<String> releaseVersionSupplier(
            ReleaseInfoProperties props,
            @Value("${fintrack.release.version-fallback:fintrack@unknown}") String fallback) {
        return new MemoisedSupplier(props.identityFile(), fallback);
    }

    /**
     * Hand-rolled memoised supplier: caches the resolved release tag in a {@code volatile} field
     * after the first call. Avoids pulling in Guava just for this one helper.
     */
    static final class MemoisedSupplier implements Supplier<String> {
        private final String identityFile;
        private final String fallback;
        private volatile String cached;

        MemoisedSupplier(String identityFile, String fallback) {
            this.identityFile = identityFile;
            this.fallback = fallback;
        }

        @Override
        public String get() {
            String local = cached;
            if (local != null) {
                return local;
            }
            synchronized (this) {
                if (cached == null) {
                    cached = resolve();
                }
                return cached;
            }
        }

        private String resolve() {
            try (BufferedReader reader = Files.newBufferedReader(Path.of(identityFile))) {
                Object loaded = new Yaml().load(reader);
                if (loaded instanceof Map<?, ?> map) {
                    Object version = map.get("version");
                    if (version != null) {
                        return "fintrack@" + version;
                    }
                }
                log.warn(
                        "IDENTITY.yaml at '{}' has no 'version' key; using fallback release tag",
                        identityFile);
            } catch (Exception e) {
                log.warn(
                        "Could not read release version from '{}': {}; using fallback release tag",
                        identityFile,
                        e.getMessage());
            }
            return fallback;
        }
    }
}
