package com.fintrack.analytics.benchmark;

import com.fintrack.analytics.benchmark.dto.BenchmarkSeries;
import com.fintrack.analytics.benchmark.dto.BenchmarkSeriesResponse;
import com.fintrack.price.client.YahooFinanceClient;
import com.fintrack.price.client.YahooFinanceClient.PricePoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class BenchmarkService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private static final List<BenchmarkSpec> SPECS = List.of(
            new BenchmarkSpec("BIST100", "XU100.IS", "TRY"),
            new BenchmarkSpec("SP500", "^GSPC", "USD"),
            new BenchmarkSpec("GOLD", "GC=F", "USD")
    );

    private final YahooFinanceClient yahoo;
    private final Map<String, CachedSeries> cache = new ConcurrentHashMap<>();

    public BenchmarkSeriesResponse fetch(int days) {
        int window = Math.max(30, Math.min(days, 365));
        List<BenchmarkSeries> seriesList = new ArrayList<>();
        for (BenchmarkSpec spec : SPECS) {
            List<BenchmarkSeries.Point> points = loadSeries(spec, window);
            seriesList.add(new BenchmarkSeries(spec.code(), spec.symbol(), spec.currency(), points));
        }
        return new BenchmarkSeriesResponse(window, seriesList);
    }

    private List<BenchmarkSeries.Point> loadSeries(BenchmarkSpec spec, int days) {
        String key = spec.symbol() + "#" + days;
        CachedSeries cached = cache.get(key);
        Instant now = Instant.now();
        if (cached != null && Duration.between(cached.fetchedAt, now).compareTo(CACHE_TTL) < 0) {
            return cached.points;
        }

        List<PricePoint> raw = yahoo.fetchHistory(spec.symbol(), days);
        List<BenchmarkSeries.Point> points = new ArrayList<>();
        for (PricePoint p : raw) {
            if (p.price() == null || p.price().signum() <= 0) continue;
            LocalDate date = p.at().atZone(ZoneOffset.UTC).toLocalDate();
            points.add(new BenchmarkSeries.Point(date, p.price()));
        }
        if (points.isEmpty() && cached != null) {
            log.warn("Benchmark {} returned empty series; keeping stale cache", spec.symbol());
            return cached.points;
        }
        cache.put(key, new CachedSeries(points, now));
        return points;
    }

    private record BenchmarkSpec(String code, String symbol, String currency) {}

    private record CachedSeries(List<BenchmarkSeries.Point> points, Instant fetchedAt) {}
}
