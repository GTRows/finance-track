package com.fintrack.analytics.montecarlo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.analytics.montecarlo.dto.AllocationClassInput;
import com.fintrack.analytics.montecarlo.dto.MonteCarloDefaultsResponse;
import com.fintrack.analytics.montecarlo.dto.MonteCarloRequest;
import com.fintrack.analytics.montecarlo.dto.MonteCarloResponse;
import com.fintrack.analytics.montecarlo.dto.YearPercentilePoint;
import com.fintrack.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MonteCarloServiceTest {

    private static ExecutorService executor;
    private static MonteCarloDefaultsLoader loader;
    private static MonteCarloService service;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeAll
    static void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        loader = new MonteCarloDefaultsLoader(MonteCarloDefaultsLoader.DEFAULT_RESOURCE_PATH);
        loader.load();
        service = new MonteCarloService(loader, executor);
    }

    @AfterAll
    static void tearDown() {
        executor.shutdownNow();
    }

    private MonteCarloRequest baseRequest(List<AllocationClassInput> allocations) {
        return new MonteCarloRequest(
                10, 1000, new BigDecimal("100000"), new BigDecimal("1000"), null, allocations);
    }

    private AllocationClassInput row(AssetClass klass, double weight, double mean, double stddev) {
        return new AllocationClassInput(
                klass,
                BigDecimal.valueOf(weight),
                BigDecimal.valueOf(mean),
                BigDecimal.valueOf(stddev));
    }

    private long[] fixedSeeds(int n, long base) {
        long[] seeds = new long[n];
        for (int i = 0; i < n; i++) seeds[i] = base + i;
        return seeds;
    }

    @Test
    void emptyAllocationsThrows() {
        MonteCarloRequest req = baseRequest(List.of());
        assertThatThrownBy(() -> service.compute(USER_ID, req, fixedSeeds(1000, 42L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("allocations required");
    }

    @Test
    void iterationsBelowOneRejected() {
        MonteCarloRequest req =
                new MonteCarloRequest(
                        10,
                        0,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        assertThatThrownBy(() -> service.compute(USER_ID, req, new long[0]))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("iterations out of range");
    }

    @Test
    void iterationsAboveCapRejected() {
        MonteCarloRequest req =
                new MonteCarloRequest(
                        10,
                        10001,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        assertThatThrownBy(() -> service.compute(USER_ID, req, fixedSeeds(10001, 42L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("iterations out of range");
    }

    @Test
    void horizonBelowOneRejected() {
        MonteCarloRequest req =
                new MonteCarloRequest(
                        0,
                        100,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        assertThatThrownBy(() -> service.compute(USER_ID, req, fixedSeeds(100, 42L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("horizonYears out of range");
    }

    @Test
    void horizonAboveCapRejected() {
        MonteCarloRequest req =
                new MonteCarloRequest(
                        51,
                        100,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        assertThatThrownBy(() -> service.compute(USER_ID, req, fixedSeeds(100, 42L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("horizonYears out of range");
    }

    @Test
    void weightsSumDeviatingFromOneRejected() {
        MonteCarloRequest req =
                new MonteCarloRequest(
                        10,
                        100,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(
                                row(AssetClass.STOCK, 0.7, 0.07, 0.18),
                                row(AssetClass.BOND, 0.5, 0.03, 0.06)));
        assertThatThrownBy(() -> service.compute(USER_ID, req, fixedSeeds(100, 42L)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MONTE_CARLO_WEIGHTS_INVALID"));
    }

    @Test
    void negativeNetWorthRejected() {
        MonteCarloRequest req =
                new MonteCarloRequest(
                        10,
                        100,
                        new BigDecimal("-1"),
                        new BigDecimal("1000"),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        assertThatThrownBy(() -> service.compute(USER_ID, req, fixedSeeds(100, 42L)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MONTE_CARLO_NET_WORTH_NEGATIVE"));
    }

    @Test
    void negativeContributionRejected() {
        MonteCarloRequest req =
                new MonteCarloRequest(
                        10,
                        100,
                        new BigDecimal("100000"),
                        new BigDecimal("-1"),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        assertThatThrownBy(() -> service.compute(USER_ID, req, fixedSeeds(100, 42L)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo("MONTE_CARLO_CONTRIBUTION_NEGATIVE"));
    }

    @Test
    void belowMinimumStddevRejected() {
        MonteCarloRequest req =
                new MonteCarloRequest(
                        10,
                        100,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.00001)));
        assertThatThrownBy(() -> service.compute(USER_ID, req, fixedSeeds(100, 42L)))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MONTE_CARLO_STDDEV_INVALID"));
    }

    @Test
    void singleClassNearDeterministicCollapsesToFutureValueFormula() {
        // Tiny stddev (0.0001 floor) with mean = 0% means percentile spread is negligible; the
        // simulated FV must hover near pv (no growth) + horizon * 12 * pmt (contributions only).
        int horizonYears = 5;
        int iterations = 1000;
        double pv = 100000.0;
        double pmt = 1000.0;
        MonteCarloRequest req =
                new MonteCarloRequest(
                        horizonYears,
                        iterations,
                        BigDecimal.valueOf(pv),
                        BigDecimal.valueOf(pmt),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.0, 0.0001)));
        MonteCarloResponse res = service.compute(USER_ID, req, fixedSeeds(iterations, 42L));

        // FV with r=0: pv + n * pmt = 100000 + 60 * 1000 = 160000.
        double expectedFv = pv + horizonYears * 12 * pmt;
        YearPercentilePoint last = res.fan().get(horizonYears - 1);
        double p10 = last.p10().doubleValue();
        double p90 = last.p90().doubleValue();
        assertThat(p10)
                .isCloseTo(expectedFv, org.assertj.core.data.Offset.offset(expectedFv * 0.01));
        assertThat(p90)
                .isCloseTo(expectedFv, org.assertj.core.data.Offset.offset(expectedFv * 0.01));
        // Spread is negligible.
        assertThat(Math.abs(p90 - p10) / expectedFv).isLessThan(0.01);
    }

    @Test
    void fanReturnsExactlyOnePointPerYear() {
        int horizonYears = 7;
        int iterations = 500;
        MonteCarloRequest req =
                new MonteCarloRequest(
                        horizonYears,
                        iterations,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(
                                row(AssetClass.STOCK, 0.5, 0.07, 0.18),
                                row(AssetClass.BOND, 0.5, 0.03, 0.06)));
        MonteCarloResponse res = service.compute(USER_ID, req, fixedSeeds(iterations, 42L));
        assertThat(res.fan()).hasSize(horizonYears);
        for (int i = 0; i < horizonYears; i++) {
            assertThat(res.fan().get(i).year()).isEqualTo(i + 1);
        }
    }

    @Test
    void percentileOrderingIsMonotonicWithinEveryYear() {
        int horizonYears = 10;
        int iterations = 500;
        MonteCarloRequest req =
                new MonteCarloRequest(
                        horizonYears,
                        iterations,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(
                                row(AssetClass.STOCK, 0.5, 0.10, 0.20),
                                row(AssetClass.BOND, 0.5, 0.03, 0.06)));
        MonteCarloResponse res = service.compute(USER_ID, req, fixedSeeds(iterations, 42L));
        for (YearPercentilePoint pt : res.fan()) {
            assertThat(pt.p10()).isLessThanOrEqualTo(pt.p25());
            assertThat(pt.p25()).isLessThanOrEqualTo(pt.p50());
            assertThat(pt.p50()).isLessThanOrEqualTo(pt.p75());
            assertThat(pt.p75()).isLessThanOrEqualTo(pt.p90());
        }
    }

    @Test
    void successProbabilityIsZeroWhenTargetAboveAllTerminals() {
        int iterations = 200;
        MonteCarloRequest req =
                new MonteCarloRequest(
                        5,
                        iterations,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        new BigDecimal("999999999999"),
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        MonteCarloResponse res = service.compute(USER_ID, req, fixedSeeds(iterations, 42L));
        assertThat(res.summary().successProbability().doubleValue()).isEqualTo(0.0);
    }

    @Test
    void successProbabilityIsOneWhenTargetBelowAllTerminals() {
        int iterations = 200;
        MonteCarloRequest req =
                new MonteCarloRequest(
                        5,
                        iterations,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        new BigDecimal("0"),
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        MonteCarloResponse res = service.compute(USER_ID, req, fixedSeeds(iterations, 42L));
        assertThat(res.summary().successProbability().doubleValue()).isEqualTo(1.0);
    }

    @Test
    void successProbabilityIsNullWhenTargetMissing() {
        int iterations = 200;
        MonteCarloRequest req =
                new MonteCarloRequest(
                        5,
                        iterations,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(row(AssetClass.STOCK, 1.0, 0.07, 0.18)));
        MonteCarloResponse res = service.compute(USER_ID, req, fixedSeeds(iterations, 42L));
        assertThat(res.summary().successProbability()).isNull();
    }

    @Test
    void defaultsAppliedEchoesResolvedTuple() {
        int iterations = 50;
        // Omit annualMeanReturn and annualStdDev for STOCK so the loader fills both from the YAML.
        AllocationClassInput stockBlank =
                new AllocationClassInput(AssetClass.STOCK, BigDecimal.ONE, null, null);
        MonteCarloRequest req =
                new MonteCarloRequest(
                        3,
                        iterations,
                        new BigDecimal("100000"),
                        new BigDecimal("1000"),
                        null,
                        List.of(stockBlank));
        MonteCarloResponse res = service.compute(USER_ID, req, fixedSeeds(iterations, 42L));
        assertThat(res.defaultsApplied()).hasSize(1);
        assertThat(res.defaultsApplied().get(0).annualMeanReturn().doubleValue()).isEqualTo(0.07);
        assertThat(res.defaultsApplied().get(0).annualStdDev().doubleValue()).isEqualTo(0.18);
    }

    @Test
    void defaultsEndpointReturnsAllEightClasses() {
        MonteCarloDefaultsResponse res = service.defaults();
        assertThat(res.classes()).hasSize(AssetClass.values().length);
        assertThat(res.defaultIterations()).isEqualTo(10000);
        assertThat(res.defaultHorizonYears()).isEqualTo(20);
    }
}
