package com.fintrack.report.tax.tr;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Eager classpath loader for {@code tax-parameters-tr.yml}.
 *
 * <p>The YAML file is the single source of truth for TR tax constants and is edited once a year by
 * the owner. The loader reads it at startup, surfaces an immutable {@link TrTaxParameters} per year
 * via {@link #findByYear(int)}, and silently degrades to an empty map on missing or malformed input
 * so a typo in the YAML never crashes the app.
 */
@Component
@Slf4j
public final class TrTaxParametersLoader {

    static final String DEFAULT_RESOURCE_PATH = "tax/tax-parameters-tr.yml";

    private final String resourcePath;
    private final Map<Integer, TrTaxParameters> byYear = new HashMap<>();

    public TrTaxParametersLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    TrTaxParametersLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.warn("TR tax parameters file not found at classpath:{}", resourcePath);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            Object raw = new Yaml().load(input);
            if (!(raw instanceof Map<?, ?> root)) {
                log.error("TR tax parameters at classpath:{} did not parse to a map", resourcePath);
                return;
            }
            String fileSource = stringOrDefault(root.get("source"), "classpath:" + resourcePath);
            Object yearsNode = root.get("years");
            if (!(yearsNode instanceof Map<?, ?> years)) {
                log.warn(
                        "TR tax parameters at classpath:{} has no 'years' map; loader is empty",
                        resourcePath);
                return;
            }
            for (Map.Entry<?, ?> entry : years.entrySet()) {
                Integer year = parseYear(entry.getKey());
                if (year == null) continue;
                if (!(entry.getValue() instanceof Map<?, ?> body)) continue;
                TrTaxParameters params = toParameters(year, body, fileSource);
                byYear.put(year, params);
            }
            log.info(
                    "Loaded TR tax parameters for {} year(s) from classpath:{}",
                    byYear.size(),
                    resourcePath);
        } catch (IOException ioe) {
            log.error(
                    "Failed to read TR tax parameters from classpath:{}: {}",
                    resourcePath,
                    ioe.getMessage());
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to parse TR tax parameters from classpath:{}: {}",
                    resourcePath,
                    ex.getMessage());
        }
    }

    public Optional<TrTaxParameters> findByYear(int year) {
        return Optional.ofNullable(byYear.get(year));
    }

    private static TrTaxParameters toParameters(int year, Map<?, ?> body, String fileSource) {
        BigDecimal threshold = toBigDecimal(body.get("capitalGainsThresholdTry"));
        List<String> appliesTo = toStringList(body.get("appliesTo"));
        BigDecimal dividendStoppageRate = toBigDecimal(body.get("dividendStoppageRate"));
        BigDecimal besDividendStoppageRate = toBigDecimal(body.get("besDividendStoppageRate"));
        String notes = stringOrDefault(body.get("notes"), null);
        return new TrTaxParameters(
                year,
                threshold,
                appliesTo,
                dividendStoppageRate,
                besDividendStoppageRate,
                notes,
                fileSource);
    }

    private static Integer parseYear(Object key) {
        if (key == null) return null;
        try {
            return Integer.parseInt(key.toString().trim());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(value.toString().trim());
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) return Collections.emptyList();
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) out.add(item.toString());
        }
        return List.copyOf(out);
    }

    private static String stringOrDefault(Object value, String fallback) {
        if (value == null) return fallback;
        String s = value.toString();
        return s.isBlank() ? fallback : s;
    }
}
