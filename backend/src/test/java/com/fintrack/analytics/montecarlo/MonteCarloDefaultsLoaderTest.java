package com.fintrack.analytics.montecarlo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.analytics.montecarlo.MonteCarloDefaultsLoader.ClassDefault;
import com.fintrack.analytics.montecarlo.MonteCarloDefaultsLoader.GlobalDefaults;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonteCarloDefaultsLoaderTest {

    @Test
    void loadsSeededClassesFromClasspath() {
        MonteCarloDefaultsLoader loader = newLoader(MonteCarloDefaultsLoader.DEFAULT_RESOURCE_PATH);

        Map<AssetClass, ClassDefault> defaults = loader.defaults();
        assertThat(defaults).containsKeys(AssetClass.values());
        ClassDefault stock = defaults.get(AssetClass.STOCK);
        assertThat(stock.annualMeanReturn().doubleValue()).isEqualTo(0.07);
        assertThat(stock.annualStdDev().doubleValue()).isEqualTo(0.18);
        assertThat(stock.defaultWeight().doubleValue()).isEqualTo(0.50);
    }

    @Test
    void globalsExposeIterationsAndHorizon() {
        MonteCarloDefaultsLoader loader = newLoader(MonteCarloDefaultsLoader.DEFAULT_RESOURCE_PATH);

        GlobalDefaults globals = loader.globals();
        assertThat(globals.iterations()).isEqualTo(10000);
        assertThat(globals.horizonYears()).isEqualTo(20);
        assertThat(globals.targetNetWorth()).isNull();
    }

    @Test
    void missingFileFallsBackCleanly() {
        MonteCarloDefaultsLoader loader = newLoader("analytics-missing/nonexistent.yml");
        assertThat(loader.defaults()).isEmpty();
        assertThat(loader.findByClass(AssetClass.STOCK)).isNull();
    }

    @Test
    void malformedYamlIsSilentlySwallowed() {
        MonteCarloDefaultsLoader loader = newLoader("analytics-malformed/monte-carlo-defaults.yml");
        assertThat(loader.defaults()).isEmpty();
    }

    @Test
    void roundTripsEightClassesFromTheDefaultYaml() {
        MonteCarloDefaultsLoader loader = newLoader(MonteCarloDefaultsLoader.DEFAULT_RESOURCE_PATH);
        for (AssetClass klass : AssetClass.values()) {
            ClassDefault d = loader.findByClass(klass);
            assertThat(d).as("class %s should be present", klass).isNotNull();
            assertThat(d.assetClass()).isEqualTo(klass);
            assertThat(d.annualStdDev().doubleValue()).isPositive();
        }
    }

    private static MonteCarloDefaultsLoader newLoader(String resourcePath) {
        MonteCarloDefaultsLoader loader = new MonteCarloDefaultsLoader(resourcePath);
        loader.load();
        return loader;
    }
}
