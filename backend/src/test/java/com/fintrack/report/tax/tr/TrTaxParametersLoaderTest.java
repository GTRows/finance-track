package com.fintrack.report.tax.tr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrTaxParametersLoaderTest {

    @Test
    void loadsSeededYearsFromClasspath() {
        TrTaxParametersLoader loader = newLoader(TrTaxParametersLoader.DEFAULT_RESOURCE_PATH);

        Optional<TrTaxParameters> y2024 = loader.findByYear(2024);
        Optional<TrTaxParameters> y2025 = loader.findByYear(2025);

        assertThat(y2024).isPresent();
        assertThat(y2024.get().capitalGainsThresholdTry()).isNotNull();
        assertThat(y2024.get().appliesTo()).contains("STOCK");
        assertThat(y2024.get().source()).contains("gib.gov.tr");

        assertThat(y2025).isPresent();
        assertThat(y2025.get().capitalGainsThresholdTry()).isNotNull();
        assertThat(y2025.get().appliesTo()).contains("STOCK");
    }

    @Test
    void unknownYearReturnsEmpty() {
        TrTaxParametersLoader loader = newLoader(TrTaxParametersLoader.DEFAULT_RESOURCE_PATH);
        assertThat(loader.findByYear(1999)).isEmpty();
    }

    @Test
    void malformedYamlIsSilentlySwallowed() {
        TrTaxParametersLoader loader = newLoader("tax-malformed/tax-parameters-tr.yml");
        assertThat(loader.findByYear(2025)).isEmpty();
        assertThat(loader.findByYear(2024)).isEmpty();
    }

    @Test
    void missingFileFallsBackCleanly() {
        TrTaxParametersLoader loader = newLoader("tax-missing/nonexistent.yml");
        assertThat(loader.findByYear(2025)).isEmpty();
    }

    private static TrTaxParametersLoader newLoader(String resourcePath) {
        TrTaxParametersLoader loader = new TrTaxParametersLoader(resourcePath);
        loader.load();
        return loader;
    }
}
