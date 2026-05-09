package com.fintrack.analytics.montecarlo;

import com.fintrack.analytics.montecarlo.MonteCarloDefaultsLoader.ClassDefault;
import com.fintrack.analytics.montecarlo.MonteCarloDefaultsLoader.GlobalDefaults;
import com.fintrack.analytics.montecarlo.dto.AllocationClassDefault;
import com.fintrack.analytics.montecarlo.dto.AllocationClassInput;
import com.fintrack.analytics.montecarlo.dto.MonteCarloDefaultsResponse;
import com.fintrack.analytics.montecarlo.dto.MonteCarloRequest;
import com.fintrack.analytics.montecarlo.dto.MonteCarloResponse;
import com.fintrack.analytics.montecarlo.dto.MonteCarloSummary;
import com.fintrack.analytics.montecarlo.dto.YearPercentilePoint;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.exception.BusinessRuleException;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only orchestrator for the Monte Carlo net-worth projection. Each iteration runs a private
 * sequential simulation ({@code horizonYears * 12} monthly steps, one normal draw per class per
 * step under that class's weight, contribution applied at end-of-month, terminal value captured).
 * Iterations themselves fan out across the {@code tracingPriceVirtualExecutor} bean from 26-01 (the
 * shared virtual-thread executor decorator); the bean is generic and unbounded so reusing it for
 * the analytics path does not contend with the price-sync hot path.
 *
 * <p>Memory budget: {@code horizonYears * iterations * 8 bytes} worst case (50y x 10k = 4 MB).
 * Compute budget: ~150-300ms for the default 20y x 10k payload on a modern JVM.
 *
 * <p>Random number generation: each iteration carries its own {@link Random} seeded from a per-task
 * seed array generated upfront via {@link SecureRandom}; tests inject a fixed seed array via the
 * package-private {@link #compute(UUID, MonteCarloRequest, long[])} overload to pin the percentile
 * output.
 */
@Service
public class MonteCarloService {

    /** Tolerance for the operator-entered allocation-weight sum: must land in [0.999, 1.001]. */
    private static final BigDecimal WEIGHT_SUM_TOLERANCE = new BigDecimal("0.001");

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal MIN_VALID_STDDEV = new BigDecimal("0.0001");

    private final MonteCarloDefaultsLoader defaultsLoader;
    private final ExecutorService executor;
    private final SecureRandom seedSource = new SecureRandom();

    public MonteCarloService(
            MonteCarloDefaultsLoader defaultsLoader,
            @Qualifier("tracingPriceVirtualExecutor") ExecutorService executor) {
        this.defaultsLoader = defaultsLoader;
        this.executor = executor;
    }

    /** Returns the YAML-backed defaults for the editor pre-fill on first render. */
    @Observed(name = "analytics.monteCarlo.defaults", contextualName = "defaults")
    @Transactional(readOnly = true)
    public MonteCarloDefaultsResponse defaults() {
        GlobalDefaults globals = defaultsLoader.globals();
        List<AllocationClassDefault> classes = new ArrayList<>();
        for (AssetClass type : AssetClass.values()) {
            ClassDefault d = defaultsLoader.findByClass(type);
            if (d == null) continue;
            classes.add(
                    new AllocationClassDefault(
                            d.assetClass(),
                            d.defaultWeight(),
                            d.annualMeanReturn(),
                            d.annualStdDev()));
        }
        return new MonteCarloDefaultsResponse(
                globals.iterations(),
                globals.horizonYears(),
                globals.monthlyContribution(),
                globals.currentNetWorth(),
                globals.targetNetWorth(),
                classes);
    }

    /** Public entry point. Delegates to the seed-overload with freshly generated seeds. */
    @Observed(name = "analytics.monteCarlo", contextualName = "compute")
    @Cacheable(
            value = CacheConfig.ANALYTICS_MONTE_CARLO_CACHE,
            key = "#userId + ':' + #request.normalisedHash()")
    @Transactional(readOnly = true)
    public MonteCarloResponse compute(UUID userId, MonteCarloRequest request) {
        return compute(userId, request, null);
    }

    /**
     * Package-private overload accepting a pre-computed seed array for deterministic tests. When
     * {@code fixedSeeds} is null fresh seeds are generated from {@link SecureRandom}.
     */
    MonteCarloResponse compute(UUID userId, MonteCarloRequest request, long[] fixedSeeds) {
        validate(request);
        ResolvedAllocations resolved = resolveAllocations(request);
        int iterations = request.iterations();
        int horizonYears = request.horizonYears();
        int totalMonths = horizonYears * 12;

        long[] seeds = fixedSeeds != null ? fixedSeeds : freshSeeds(iterations);
        if (seeds.length != iterations) {
            throw new IllegalArgumentException("fixedSeeds length must equal request.iterations()");
        }

        double pv = request.currentNetWorth().doubleValue();
        double pmt = request.monthlyContribution().doubleValue();

        // terminalsByYear[year - 1][iterationIndex] = end-of-year balance for that path.
        double[][] terminalsByYear = new double[horizonYears][iterations];

        List<CompletableFuture<Void>> futures = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            final int iterationIndex = i;
            final long seed = seeds[i];
            futures.add(
                    CompletableFuture.runAsync(
                            () ->
                                    simulatePath(
                                            iterationIndex,
                                            seed,
                                            pv,
                                            pmt,
                                            horizonYears,
                                            totalMonths,
                                            resolved,
                                            terminalsByYear),
                            executor));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<YearPercentilePoint> fan = aggregatePercentiles(terminalsByYear, horizonYears);
        MonteCarloSummary summary =
                buildSummary(terminalsByYear[horizonYears - 1], request.targetNetWorth());
        return new MonteCarloResponse(
                horizonYears,
                iterations,
                request.currentNetWorth(),
                request.monthlyContribution(),
                request.targetNetWorth(),
                fan,
                summary,
                resolved.applied());
    }

    private static void simulatePath(
            int iterationIndex,
            long seed,
            double pv,
            double pmt,
            int horizonYears,
            int totalMonths,
            ResolvedAllocations resolved,
            double[][] terminalsByYear) {
        Random rng = new Random(seed);
        double balance = pv;
        int classes = resolved.weights().length;
        double[] weights = resolved.weights();
        double[] monthlyMeans = resolved.monthlyMeans();
        double[] monthlyStdDevs = resolved.monthlyStdDevs();

        for (int month = 1; month <= totalMonths; month++) {
            double portfolioReturn = 0.0;
            for (int c = 0; c < classes; c++) {
                double draw = monthlyMeans[c] + monthlyStdDevs[c] * rng.nextGaussian();
                portfolioReturn += weights[c] * draw;
            }
            balance = balance * (1.0 + portfolioReturn) + pmt;
            if (month % 12 == 0) {
                int yearIndex = (month / 12) - 1;
                terminalsByYear[yearIndex][iterationIndex] = balance;
            }
        }
        // Capture terminal of the last year if it hasn't been captured (totalMonths is always
        // horizonYears * 12 so it always lands; this guard is defensive).
        if (totalMonths % 12 != 0) {
            terminalsByYear[horizonYears - 1][iterationIndex] = balance;
        }
    }

    private static List<YearPercentilePoint> aggregatePercentiles(
            double[][] terminalsByYear, int horizonYears) {
        List<YearPercentilePoint> fan = new ArrayList<>(horizonYears);
        for (int y = 0; y < horizonYears; y++) {
            double[] sorted = terminalsByYear[y].clone();
            Arrays.sort(sorted);
            BigDecimal p10 = percentile(sorted, 0.10);
            BigDecimal p25 = percentile(sorted, 0.25);
            BigDecimal p50 = percentile(sorted, 0.50);
            BigDecimal p75 = percentile(sorted, 0.75);
            BigDecimal p90 = percentile(sorted, 0.90);
            fan.add(new YearPercentilePoint(y + 1, p10, p25, p50, p75, p90));
        }
        return fan;
    }

    private static BigDecimal percentile(double[] sorted, double q) {
        if (sorted.length == 0) return BigDecimal.ZERO;
        if (sorted.length == 1) return roundMoney(sorted[0]);
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return roundMoney(sorted[lo]);
        double frac = pos - lo;
        double v = sorted[lo] + frac * (sorted[hi] - sorted[lo]);
        return roundMoney(v);
    }

    private static MonteCarloSummary buildSummary(double[] terminals, BigDecimal target) {
        double[] sorted = terminals.clone();
        Arrays.sort(sorted);
        double sum = 0.0;
        for (double v : sorted) sum += v;
        BigDecimal mean = roundMoney(sum / sorted.length);
        BigDecimal p10 = percentile(sorted, 0.10);
        BigDecimal p50 = percentile(sorted, 0.50);
        BigDecimal p90 = percentile(sorted, 0.90);
        BigDecimal successProbability = null;
        if (target != null) {
            double t = target.doubleValue();
            int hits = 0;
            for (double v : terminals) {
                if (v >= t) hits++;
            }
            successProbability =
                    BigDecimal.valueOf((double) hits / terminals.length)
                            .setScale(4, RoundingMode.HALF_UP);
        }
        return new MonteCarloSummary(mean, p10, p50, p90, successProbability);
    }

    private void validate(MonteCarloRequest request) {
        if (request.allocations() == null || request.allocations().isEmpty()) {
            throw new BusinessRuleException(
                    "allocations required", "MONTE_CARLO_ALLOCATIONS_REQUIRED");
        }
        int iterations = request.iterations();
        if (iterations < 1 || iterations > 10000) {
            throw new BusinessRuleException(
                    "iterations out of range", "MONTE_CARLO_ITERATIONS_OUT_OF_RANGE");
        }
        int horizonYears = request.horizonYears();
        if (horizonYears < 1 || horizonYears > 50) {
            throw new BusinessRuleException(
                    "horizonYears out of range", "MONTE_CARLO_HORIZON_OUT_OF_RANGE");
        }
        if (request.currentNetWorth().signum() < 0) {
            throw new BusinessRuleException(
                    "currentNetWorth negative", "MONTE_CARLO_NET_WORTH_NEGATIVE");
        }
        if (request.monthlyContribution().signum() < 0) {
            throw new BusinessRuleException(
                    "monthlyContribution negative", "MONTE_CARLO_CONTRIBUTION_NEGATIVE");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (AllocationClassInput row : request.allocations()) {
            if (row.weight() == null || row.weight().signum() < 0) {
                throw new BusinessRuleException(
                        "weight must be in [0,1]", "MONTE_CARLO_WEIGHTS_INVALID");
            }
            sum = sum.add(row.weight());
            BigDecimal stddev = row.annualStdDev();
            if (stddev != null && stddev.compareTo(MIN_VALID_STDDEV) < 0) {
                throw new BusinessRuleException(
                        "annualStdDev must be >= 0.0001", "MONTE_CARLO_STDDEV_INVALID");
            }
        }
        BigDecimal lower = ONE.subtract(WEIGHT_SUM_TOLERANCE);
        BigDecimal upper = ONE.add(WEIGHT_SUM_TOLERANCE);
        if (sum.compareTo(lower) < 0 || sum.compareTo(upper) > 0) {
            throw new BusinessRuleException(
                    "weights must sum to 1.0 (+/-0.001)", "MONTE_CARLO_WEIGHTS_INVALID");
        }
    }

    private ResolvedAllocations resolveAllocations(MonteCarloRequest request) {
        int n = request.allocations().size();
        double[] weights = new double[n];
        double[] monthlyMeans = new double[n];
        double[] monthlyStdDevs = new double[n];
        List<AllocationClassDefault> applied = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            AllocationClassInput row = request.allocations().get(i);
            ClassDefault fallback = defaultsLoader.findByClass(row.assetClass());
            BigDecimal mean = row.annualMeanReturn();
            if (mean == null) {
                mean = fallback != null ? fallback.annualMeanReturn() : BigDecimal.ZERO;
            }
            BigDecimal stddev = row.annualStdDev();
            if (stddev == null) {
                if (fallback == null) {
                    throw new BusinessRuleException(
                            "stddev missing and no default for class "
                                    + row.assetClass()
                                    + "; supply annualStdDev",
                            "MONTE_CARLO_STDDEV_INVALID");
                }
                stddev = fallback.annualStdDev();
            }
            if (stddev.compareTo(MIN_VALID_STDDEV) < 0) {
                throw new BusinessRuleException(
                        "annualStdDev must be >= 0.0001", "MONTE_CARLO_STDDEV_INVALID");
            }
            weights[i] = row.weight().doubleValue();
            double annualMean = mean.doubleValue();
            double annualStd = stddev.doubleValue();
            monthlyMeans[i] = annualMean / 12.0;
            monthlyStdDevs[i] = annualStd / Math.sqrt(12.0);
            applied.add(new AllocationClassDefault(row.assetClass(), row.weight(), mean, stddev));
        }
        return new ResolvedAllocations(weights, monthlyMeans, monthlyStdDevs, applied);
    }

    private long[] freshSeeds(int iterations) {
        long[] seeds = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            seeds[i] = seedSource.nextLong();
        }
        return seeds;
    }

    private static BigDecimal roundMoney(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private record ResolvedAllocations(
            double[] weights,
            double[] monthlyMeans,
            double[] monthlyStdDevs,
            List<AllocationClassDefault> applied) {}
}
