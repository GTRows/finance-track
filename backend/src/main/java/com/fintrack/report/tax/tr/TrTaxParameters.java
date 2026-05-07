package com.fintrack.report.tax.tr;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable snapshot of TR tax parameters for one fiscal year.
 *
 * <p>Loaded from {@code classpath:tax/tax-parameters-tr.yml} by {@link TrTaxParametersLoader}. The
 * {@code source} field carries the YAML's top-level {@code source:} string (or the resource path
 * when the YAML omits it) so the report response can echo build-time provenance to the owner.
 */
public record TrTaxParameters(
        int year,
        BigDecimal capitalGainsThresholdTry,
        List<String> appliesTo,
        BigDecimal dividendStoppageRate,
        BigDecimal besDividendStoppageRate,
        String notes,
        String source) {}
