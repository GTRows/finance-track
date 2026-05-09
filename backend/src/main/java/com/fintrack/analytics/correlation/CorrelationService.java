package com.fintrack.analytics.correlation;

import com.fintrack.analytics.correlation.dto.CorrelationMatrixResponse;
import com.fintrack.analytics.correlation.dto.SamplePeriod;
import com.fintrack.asset.AssetRepository;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PriceHistory;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.price.PriceHistoryRepository;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only orchestrator for the asset correlation matrix view. Loads each requested asset's daily
 * close-of-day price series from {@code price_history}, computes log-return series with pair-wise
 * date intersection (NOT forward-fill — forward-fill biases correlations toward 1.0), and runs
 * either a Pearson or Spearman kernel over the aligned series.
 *
 * <p>Capped at 25 assets per request to keep the response payload bounded (25^2 = 625 cells) and to
 * stay within the heatmap's visual budget on a 1280px page. The service de-duplicates incoming
 * asset ids preserving submission order; cache keys sort the ids so {@code [A,B,C]} and {@code
 * [C,B,A]} share a Caffeine entry.
 *
 * <p>Sparse data: cells where the pair has fewer than 2 overlapping returns or either series has
 * zero stddev are emitted as {@code null} (the frontend renders these as "n/a"). The diagonal is
 * 1.0 for valid rows and {@code null} for degenerate ones.
 */
@Service
@RequiredArgsConstructor
public class CorrelationService {

    /** Hard cap on the number of assets per correlation request. */
    public static final int MAX_ASSETS = 25;

    /** Maximum window length in days; matches the {@code price_history} retention policy. */
    public static final int MAX_WINDOW_DAYS = 90;

    private final AssetRepository assetRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    /**
     * Returns a square correlation matrix over the requested assets. Cached on a sorted-id key so
     * reordering the input does not produce a cache miss.
     */
    @Observed(name = "analytics.correlations", contextualName = "compute")
    @Cacheable(
            value = CacheConfig.ANALYTICS_CORRELATIONS_CACHE,
            key =
                    "#userId + ':' + T(java.lang.String).join(',',"
                        + " #assetIds.stream().map(T(java.util.UUID)::toString).sorted().toList())"
                        + " + ':' + (#from != null ? #from.toString() : 'null') + ':' + (#to !="
                        + " null ? #to.toString() : 'null') + ':' + #method.name()")
    @Transactional(readOnly = true)
    public CorrelationMatrixResponse compute(
            UUID userId,
            List<UUID> assetIds,
            LocalDate from,
            LocalDate to,
            CorrelationMethod method) {
        List<UUID> deduped = dedupePreserveOrder(assetIds);
        if (deduped.isEmpty()) {
            throw new BusinessRuleException("assetIds required", "CORRELATION_IDS_REQUIRED");
        }
        if (deduped.size() < 2) {
            throw new BusinessRuleException("At least 2 assets required", "CORRELATION_TOO_FEW");
        }
        if (deduped.size() > MAX_ASSETS) {
            throw new BusinessRuleException(
                    "Too many assets in correlation request", "CORRELATION_TOO_MANY");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("Invalid range", "CORRELATION_RANGE_INVALID");
        }

        // Default range: last 90 days (matches price_history retention). When operator passes a
        // wider window we clamp `from` to today - 90d so the cache key stays stable.
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate windowFloor = today.minusDays(MAX_WINDOW_DAYS);
        LocalDate effectiveFrom = from != null ? from : windowFloor;
        if (effectiveFrom.isBefore(windowFloor)) {
            effectiveFrom = windowFloor;
        }

        // Resolve and validate assets in submission order. Assets are seeded globally
        // (V2__seed_assets.sql) so ownership is defined by existence, not by per-user mapping.
        List<Asset> assets = new ArrayList<>(deduped.size());
        for (UUID assetId : deduped) {
            Asset asset =
                    assetRepository
                            .findById(assetId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Asset not found: " + assetId));
            assets.add(asset);
        }

        // Load each asset's daily-close series within the window. Multiple intra-day rows collapse
        // by calendar UTC date keeping the latest recordedAt row.
        Instant fromInstant = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TreeMap<LocalDate, Double>> dailyPrices = new ArrayList<>(assets.size());
        for (Asset asset : assets) {
            List<PriceHistory> rows =
                    priceHistoryRepository.findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                            asset.getId(), fromInstant, toInstant);
            dailyPrices.add(collapseToDailyClose(rows));
        }

        // Compute the global N-way intersection size for the headline `alignedDays` figure.
        int alignedDays = computeGlobalIntersectionSize(dailyPrices);

        // Build the symmetric matrix.
        int n = assets.size();
        Double[][] matrix = new Double[n][n];
        Integer[][] dataPoints = new Integer[n][n];

        // Self-correlation cells (diagonal): 1.0 if the asset has at least 2 returns and non-zero
        // stddev, otherwise null.
        for (int i = 0; i < n; i++) {
            double[] returnsI = logReturnsFromAlignedKeys(dailyPrices.get(i), dailyPrices.get(i));
            if (returnsI.length < 2 || stddev(returnsI) == 0.0) {
                matrix[i][i] = null;
            } else {
                matrix[i][i] = 1.0;
            }
            dataPoints[i][i] = dailyPrices.get(i).size();
        }

        // Off-diagonal cells: pair-wise intersection then Pearson (or Spearman-on-ranks).
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                PairReturns pair = pairReturns(dailyPrices.get(i), dailyPrices.get(j));
                Double value = correlate(pair.x(), pair.y(), method);
                matrix[i][j] = value;
                matrix[j][i] = value;
                dataPoints[i][j] = pair.x().length;
                dataPoints[j][i] = pair.x().length;
            }
        }

        return new CorrelationMatrixResponse(
                deduped,
                assets.stream().map(Asset::getSymbol).toList(),
                assets.stream().map(Asset::getName).toList(),
                toListOfLists(matrix),
                toListOfListsInt(dataPoints),
                new SamplePeriod(effectiveFrom, effectiveTo, alignedDays),
                method.name());
    }

    private static List<UUID> dedupePreserveOrder(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    /**
     * Collapses possibly multiple intra-day price rows to one entry per calendar UTC date keeping
     * the latest {@code recordedAt} row.
     */
    private static TreeMap<LocalDate, Double> collapseToDailyClose(List<PriceHistory> rows) {
        TreeMap<LocalDate, Double> daily = new TreeMap<>();
        // Rows arrive ordered ASC by recordedAt; latest wins per date by overwriting.
        Map<LocalDate, Instant> latestSeen = new LinkedHashMap<>();
        for (PriceHistory row : rows) {
            if (row.getPrice() == null) continue;
            LocalDate date = row.getRecordedAt().atZone(ZoneOffset.UTC).toLocalDate();
            Instant prev = latestSeen.get(date);
            if (prev == null || row.getRecordedAt().isAfter(prev)) {
                latestSeen.put(date, row.getRecordedAt());
                daily.put(date, row.getPrice().doubleValue());
            }
        }
        return daily;
    }

    private static int computeGlobalIntersectionSize(List<TreeMap<LocalDate, Double>> series) {
        if (series.isEmpty()) return 0;
        Set<LocalDate> intersection = new HashSet<>(series.get(0).keySet());
        for (int i = 1; i < series.size(); i++) {
            intersection.retainAll(series.get(i).keySet());
        }
        return intersection.size();
    }

    /** Output of the pair-wise intersection + log-return derivation. */
    private record PairReturns(double[] x, double[] y) {}

    private static PairReturns pairReturns(
            TreeMap<LocalDate, Double> a, TreeMap<LocalDate, Double> b) {
        Set<LocalDate> common = new HashSet<>(a.keySet());
        common.retainAll(b.keySet());
        if (common.size() < 2) return new PairReturns(new double[0], new double[0]);
        List<LocalDate> sorted = new ArrayList<>(common);
        sorted.sort(Comparator.naturalOrder());
        int m = sorted.size();
        double[] x = new double[m - 1];
        double[] y = new double[m - 1];
        for (int t = 1; t < m; t++) {
            double prevA = a.get(sorted.get(t - 1));
            double curA = a.get(sorted.get(t));
            double prevB = b.get(sorted.get(t - 1));
            double curB = b.get(sorted.get(t));
            x[t - 1] = Math.log(curA / prevA);
            y[t - 1] = Math.log(curB / prevB);
        }
        return new PairReturns(x, y);
    }

    /**
     * For self-correlation rows the input maps are the same; we still derive a return series so the
     * "insufficient data" check (n &lt; 2 or stddev = 0) reuses the pair kernel.
     */
    private static double[] logReturnsFromAlignedKeys(
            TreeMap<LocalDate, Double> a, TreeMap<LocalDate, Double> b) {
        return pairReturns(a, b).x();
    }

    /**
     * Computes Pearson (or Spearman-on-ranks) correlation between two equal-length return series.
     * Returns {@code null} when either series has fewer than 2 elements or zero stddev.
     */
    private static Double correlate(double[] x, double[] y, CorrelationMethod method) {
        if (x.length < 2 || y.length < 2 || x.length != y.length) return null;
        double[] xs = method == CorrelationMethod.SPEARMAN ? toRanks(x) : x;
        double[] ys = method == CorrelationMethod.SPEARMAN ? toRanks(y) : y;
        return pearsonKernel(xs, ys);
    }

    private static Double pearsonKernel(double[] x, double[] y) {
        double meanX = mean(x);
        double meanY = mean(y);
        double num = 0.0;
        double sumDx2 = 0.0;
        double sumDy2 = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            num += dx * dy;
            sumDx2 += dx * dx;
            sumDy2 += dy * dy;
        }
        if (sumDx2 == 0.0 || sumDy2 == 0.0) return null;
        double r = num / Math.sqrt(sumDx2 * sumDy2);
        // Clip to [-1, 1] to absorb floating-point wobble (1.0000000002 etc.).
        if (r > 1.0) r = 1.0;
        if (r < -1.0) r = -1.0;
        return r;
    }

    private static double mean(double[] xs) {
        double s = 0.0;
        for (double v : xs) s += v;
        return s / xs.length;
    }

    private static double stddev(double[] xs) {
        if (xs.length == 0) return 0.0;
        double m = mean(xs);
        double s = 0.0;
        for (double v : xs) {
            double d = v - m;
            s += d * d;
        }
        return Math.sqrt(s / xs.length);
    }

    /**
     * Returns 1-based ranks of the input. Ties are resolved via average rank (the standard Spearman
     * convention).
     */
    private static double[] toRanks(double[] xs) {
        int n = xs.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> xs[i]));
        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && xs[idx[j + 1]] == xs[idx[i]]) j++;
            // Tied indices [i..j] share the average rank ((i+1) + (j+1)) / 2.
            double avgRank = ((i + 1) + (j + 1)) / 2.0;
            for (int k = i; k <= j; k++) ranks[idx[k]] = avgRank;
            i = j + 1;
        }
        return ranks;
    }

    private static List<List<Double>> toListOfLists(Double[][] grid) {
        List<List<Double>> out = new ArrayList<>(grid.length);
        for (Double[] row : grid) {
            List<Double> r = new ArrayList<>(row.length);
            for (Double v : row) r.add(v);
            out.add(r);
        }
        return out;
    }

    private static List<List<Integer>> toListOfListsInt(Integer[][] grid) {
        List<List<Integer>> out = new ArrayList<>(grid.length);
        for (Integer[] row : grid) {
            List<Integer> r = new ArrayList<>(row.length);
            for (Integer v : row) r.add(v);
            out.add(r);
        }
        return out;
    }
}
