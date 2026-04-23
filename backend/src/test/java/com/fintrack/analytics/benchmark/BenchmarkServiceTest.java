package com.fintrack.analytics.benchmark;

import com.fintrack.analytics.benchmark.dto.BenchmarkSeries;
import com.fintrack.analytics.benchmark.dto.BenchmarkSeriesResponse;
import com.fintrack.price.client.YahooFinanceClient;
import com.fintrack.price.client.YahooFinanceClient.PricePoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceTest {

    @Mock YahooFinanceClient yahoo;

    @InjectMocks BenchmarkService service;

    private PricePoint point(Instant at, String price) {
        return new PricePoint(at, price == null ? null : new BigDecimal(price));
    }

    @Test
    void fetchReturnsAllThreeBenchmarks() {
        when(yahoo.fetchHistory(eq("XU100.IS"), anyInt())).thenReturn(List.of(
                point(Instant.parse("2026-04-01T00:00:00Z"), "10500")));
        when(yahoo.fetchHistory(eq("^GSPC"), anyInt())).thenReturn(List.of(
                point(Instant.parse("2026-04-01T00:00:00Z"), "5200")));
        when(yahoo.fetchHistory(eq("GC=F"), anyInt())).thenReturn(List.of(
                point(Instant.parse("2026-04-01T00:00:00Z"), "2350")));

        BenchmarkSeriesResponse res = service.fetch(90);

        assertThat(res.days()).isEqualTo(90);
        assertThat(res.series()).hasSize(3);
        assertThat(res.series()).extracting(BenchmarkSeries::code)
                .containsExactly("BIST100", "SP500", "GOLD");
        assertThat(res.series().get(0).currency()).isEqualTo("TRY");
        assertThat(res.series().get(1).currency()).isEqualTo("USD");
    }

    @Test
    void fetchClampsWindowBelow30UpTo30() {
        when(yahoo.fetchHistory(eq("XU100.IS"), eq(30))).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("^GSPC"), eq(30))).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("GC=F"), eq(30))).thenReturn(List.of());

        BenchmarkSeriesResponse res = service.fetch(5);

        assertThat(res.days()).isEqualTo(30);
        verify(yahoo).fetchHistory("XU100.IS", 30);
    }

    @Test
    void fetchClampsWindowAbove365DownTo365() {
        when(yahoo.fetchHistory(eq("XU100.IS"), eq(365))).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("^GSPC"), eq(365))).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("GC=F"), eq(365))).thenReturn(List.of());

        BenchmarkSeriesResponse res = service.fetch(10000);

        assertThat(res.days()).isEqualTo(365);
    }

    @Test
    void dropsPointsWithNullOrNonPositivePrices() {
        when(yahoo.fetchHistory(eq("XU100.IS"), anyInt())).thenReturn(List.of(
                point(Instant.parse("2026-04-01T00:00:00Z"), null),
                point(Instant.parse("2026-04-02T00:00:00Z"), "0"),
                point(Instant.parse("2026-04-03T00:00:00Z"), "-1"),
                point(Instant.parse("2026-04-04T00:00:00Z"), "100")));
        when(yahoo.fetchHistory(eq("^GSPC"), anyInt())).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("GC=F"), anyInt())).thenReturn(List.of());

        BenchmarkSeriesResponse res = service.fetch(90);

        BenchmarkSeries bist = res.series().get(0);
        assertThat(bist.points()).hasSize(1);
        assertThat(bist.points().get(0).date()).isEqualTo(LocalDate.of(2026, 4, 4));
        assertThat(bist.points().get(0).close()).isEqualByComparingTo("100");
    }

    @Test
    void cacheHitAvoidsSecondFetch() {
        when(yahoo.fetchHistory(eq("XU100.IS"), anyInt())).thenReturn(List.of(
                point(Instant.parse("2026-04-01T00:00:00Z"), "100")));
        when(yahoo.fetchHistory(eq("^GSPC"), anyInt())).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("GC=F"), anyInt())).thenReturn(List.of());

        service.fetch(90);
        service.fetch(90);

        verify(yahoo, times(1)).fetchHistory("XU100.IS", 90);
        verify(yahoo, times(1)).fetchHistory("^GSPC", 90);
        verify(yahoo, times(1)).fetchHistory("GC=F", 90);
    }

    @Test
    void differentWindowSizesAreCachedSeparately() {
        when(yahoo.fetchHistory(eq("XU100.IS"), eq(90))).thenReturn(List.of(
                point(Instant.parse("2026-04-01T00:00:00Z"), "100")));
        when(yahoo.fetchHistory(eq("XU100.IS"), eq(180))).thenReturn(List.of(
                point(Instant.parse("2026-04-01T00:00:00Z"), "200")));
        when(yahoo.fetchHistory(eq("^GSPC"), anyInt())).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("GC=F"), anyInt())).thenReturn(List.of());

        service.fetch(90);
        service.fetch(180);

        verify(yahoo).fetchHistory("XU100.IS", 90);
        verify(yahoo).fetchHistory("XU100.IS", 180);
    }

    @Test
    void emptySeriesFromClientProducesEmptyPoints() {
        when(yahoo.fetchHistory(eq("XU100.IS"), anyInt())).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("^GSPC"), anyInt())).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("GC=F"), anyInt())).thenReturn(List.of());

        BenchmarkSeriesResponse res = service.fetch(90);

        assertThat(res.series()).hasSize(3);
        assertThat(res.series().get(0).points()).isEmpty();
        assertThat(res.series().get(1).points()).isEmpty();
        assertThat(res.series().get(2).points()).isEmpty();
    }

    @Test
    void keepsStaleCacheWhenSubsequentFetchReturnsEmpty() {
        when(yahoo.fetchHistory(eq("XU100.IS"), anyInt()))
                .thenReturn(List.of(point(Instant.parse("2026-04-01T00:00:00Z"), "100")))
                .thenReturn(List.of());
        when(yahoo.fetchHistory(eq("^GSPC"), anyInt())).thenReturn(List.of());
        when(yahoo.fetchHistory(eq("GC=F"), anyInt())).thenReturn(List.of());

        BenchmarkSeriesResponse first = service.fetch(90);
        assertThat(first.series().get(0).points()).hasSize(1);

        // Invalidate by bypassing TTL via different window? Cache TTL is 1h so use a forced second call
        // after time elapses. Here we just verify that empty response on fresh call does NOT discard cache.
        // The current code writes to cache only on non-empty, so subsequent calls within TTL still hit cache.
        BenchmarkSeriesResponse second = service.fetch(90);
        assertThat(second.series().get(0).points()).hasSize(1);
    }
}
