package com.fintrack.analytics.montecarlo;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Eager classpath loader for {@code monte-carlo-defaults.yml}. Mirrors the {@code
 * TrTaxParametersLoader} pattern byte-for-byte: {@code @PostConstruct} reads the file via
 * SnakeYAML, builds an immutable per-class map, and silently degrades to an empty map on missing or
 * malformed input so a typo in the YAML never crashes boot.
 */
@Component
@Slf4j
public final class MonteCarloDefaultsLoader {

    static final String DEFAULT_RESOURCE_PATH = "analytics/monte-carlo-defaults.yml";

    private final String resourcePath;
    private final Map<AssetClass, ClassDefault> byClass = new EnumMap<>(AssetClass.class);
    private GlobalDefaults globalDefaults = GlobalDefaults.empty();

    public MonteCarloDefaultsLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    MonteCarloDefaultsLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.warn("Monte Carlo defaults file not found at classpath:{}", resourcePath);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            Object raw = new Yaml().load(input);
            if (!(raw instanceof Map<?, ?> root)) {
                log.error(
                        "Monte Carlo defaults at classpath:{} did not parse to a map",
                        resourcePath);
                return;
            }
            globalDefaults = parseGlobalDefaults(root.get("defaults"));
            Object classesNode = root.get("classes");
            if (!(classesNode instanceof Map<?, ?> classes)) {
                log.warn(
                        "Monte Carlo defaults at classpath:{} has no 'classes' map; loader is"
                                + " empty",
                        resourcePath);
                return;
            }
            for (Map.Entry<?, ?> entry : classes.entrySet()) {
                AssetClass key = parseAssetClass(entry.getKey());
                if (key == null) continue;
                if (!(entry.getValue() instanceof Map<?, ?> body)) continue;
                BigDecimal weight = toBigDecimal(body.get("defaultWeight"));
                BigDecimal mean = toBigDecimal(body.get("annualMeanReturn"));
                BigDecimal stddev = toBigDecimal(body.get("annualStdDev"));
                if (weight == null || mean == null || stddev == null) continue;
                byClass.put(key, new ClassDefault(key, weight, mean, stddev));
            }
            log.info(
                    "Loaded Monte Carlo defaults for {} class(es) from classpath:{}",
                    byClass.size(),
                    resourcePath);
        } catch (IOException ioe) {
            log.error(
                    "Failed to read Monte Carlo defaults from classpath:{}: {}",
                    resourcePath,
                    ioe.getMessage());
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to parse Monte Carlo defaults from classpath:{}: {}",
                    resourcePath,
                    ex.getMessage());
        }
    }

    public Map<AssetClass, ClassDefault> defaults() {
        return Map.copyOf(byClass);
    }

    public ClassDefault findByClass(AssetClass assetClass) {
        return byClass.get(assetClass);
    }

    public GlobalDefaults globals() {
        return globalDefaults;
    }

    private static GlobalDefaults parseGlobalDefaults(Object node) {
        if (!(node instanceof Map<?, ?> body)) return GlobalDefaults.empty();
        Integer iterations = toInteger(body.get("iterations"));
        Integer horizonYears = toInteger(body.get("horizonYears"));
        BigDecimal currentNetWorth = toBigDecimal(body.get("currentNetWorth"));
        BigDecimal monthlyContribution = toBigDecimal(body.get("monthlyContribution"));
        BigDecimal targetNetWorth = toBigDecimal(body.get("targetNetWorth"));
        return new GlobalDefaults(
                iterations != null ? iterations : 10000,
                horizonYears != null ? horizonYears : 20,
                currentNetWorth != null ? currentNetWorth : BigDecimal.ZERO,
                monthlyContribution != null ? monthlyContribution : BigDecimal.ZERO,
                targetNetWorth);
    }

    private static AssetClass parseAssetClass(Object key) {
        if (key == null) return null;
        try {
            return AssetClass.valueOf(key.toString().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        String s = value.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /** Per-class default tuple. */
    public record ClassDefault(
            AssetClass assetClass,
            BigDecimal defaultWeight,
            BigDecimal annualMeanReturn,
            BigDecimal annualStdDev) {}

    /** Top-level simulation defaults from the {@code defaults:} block. */
    public record GlobalDefaults(
            int iterations,
            int horizonYears,
            BigDecimal currentNetWorth,
            BigDecimal monthlyContribution,
            BigDecimal targetNetWorth) {

        static GlobalDefaults empty() {
            return new GlobalDefaults(10000, 20, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
    }
}
