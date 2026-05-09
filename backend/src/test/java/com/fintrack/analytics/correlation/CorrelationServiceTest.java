package com.fintrack.analytics.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fintrack.analytics.correlation.dto.CorrelationMatrixResponse;
import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PriceHistory;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.price.PriceHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorrelationServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock PriceHistoryRepository priceHistoryRepository;

    @InjectMocks CorrelationService service;

    private final UUID userId = UUID.randomUUID();

    private Asset asset(UUID id, String symbol) {
        return Asset.builder()
                .id(id)
                .symbol(symbol)
                .name(symbol)
                .assetType(Asset.AssetType.CRYPTO)
                .currency("TRY")
                .build();
    }

    private PriceHistory price(UUID assetId, LocalDate date, String value) {
        return PriceHistory.builder()
                .assetId(assetId)
                .price(new BigDecimal(value))
                .recordedAt(date.atStartOfDay(ZoneOffset.UTC).toInstant())
                .build();
    }

    private void stubAsset(UUID id, String symbol) {
        lenient().when(assetRepository.findById(id)).thenReturn(Optional.of(asset(id, symbol)));
    }

    private void stubSeries(UUID id, List<PriceHistory> rows) {
        lenient()
                .when(
                        priceHistoryRepository
                                .findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                                        eq(id), any(), any()))
                .thenReturn(rows);
    }

    @Test
    void emptyAssetIdsThrowsBusinessRule() {
        assertThatThrownBy(
                        () ->
                                service.compute(
                                        userId, List.of(), null, null, CorrelationMethod.PEARSON))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("assetIds required");
    }

    @Test
    void singleAssetIdRejected() {
        UUID a = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                service.compute(
                                        userId, List.of(a), null, null, CorrelationMethod.PEARSON))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("At least 2 assets");
    }

    @Test
    void uncorrelatedSeriesYieldsLowMagnitude() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        // Hand-tuned series whose log-returns sum-of-products is near zero.
        // A returns: +0.05, -0.05, +0.05, -0.05
        // B returns: +0.05, +0.05, -0.05, -0.05
        // Pearson r ~ 0.
        stubSeries(
                a,
                List.of(
                        price(a, LocalDate.of(2026, 1, 1), "100"),
                        price(a, LocalDate.of(2026, 1, 2), String.valueOf(100 * Math.exp(0.05))),
                        price(a, LocalDate.of(2026, 1, 3), "100"),
                        price(a, LocalDate.of(2026, 1, 4), String.valueOf(100 * Math.exp(0.05))),
                        price(a, LocalDate.of(2026, 1, 5), "100")));
        stubSeries(
                b,
                List.of(
                        price(b, LocalDate.of(2026, 1, 1), "100"),
                        price(b, LocalDate.of(2026, 1, 2), String.valueOf(100 * Math.exp(0.05))),
                        price(b, LocalDate.of(2026, 1, 3), String.valueOf(100 * Math.exp(0.10))),
                        price(b, LocalDate.of(2026, 1, 4), String.valueOf(100 * Math.exp(0.05))),
                        price(b, LocalDate.of(2026, 1, 5), "100")));

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.PEARSON);

        Double offDiag = res.matrix().get(0).get(1);
        assertThat(offDiag).isNotNull();
        assertThat(Math.abs(offDiag)).isLessThan(0.5);
    }

    @Test
    void variedButPerfectlyCorrelatedSeriesReturnsOne() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        // Varied prices for A, B = 2 * A on every day. Log returns are identical -> r = 1.0.
        stubSeries(
                a,
                List.of(
                        price(a, LocalDate.of(2026, 1, 1), "100"),
                        price(a, LocalDate.of(2026, 1, 2), "110"),
                        price(a, LocalDate.of(2026, 1, 3), "105"),
                        price(a, LocalDate.of(2026, 1, 4), "120"),
                        price(a, LocalDate.of(2026, 1, 5), "115")));
        stubSeries(
                b,
                List.of(
                        price(b, LocalDate.of(2026, 1, 1), "200"),
                        price(b, LocalDate.of(2026, 1, 2), "220"),
                        price(b, LocalDate.of(2026, 1, 3), "210"),
                        price(b, LocalDate.of(2026, 1, 4), "240"),
                        price(b, LocalDate.of(2026, 1, 5), "230")));

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.PEARSON);

        Double offDiag = res.matrix().get(0).get(1);
        assertThat(offDiag).isNotNull();
        assertThat(offDiag).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        // Symmetric.
        assertThat(res.matrix().get(1).get(0))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        // Diagonal = 1.0 for valid series.
        assertThat(res.matrix().get(0).get(0))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(res.matrix().get(1).get(1))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
        // Pair-wise data points: 5 prices -> 4 returns.
        assertThat(res.dataPoints().get(0).get(1)).isEqualTo(4);
    }

    @Test
    void anticorrelatedSeriesReturnsMinusOne() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        // B's log returns are exactly -1 * A's. Construct: A goes 100 -> 110 -> 100 -> 110.
        // Returns: ln(1.1), ln(10/11), ln(1.1). For B we want returns ln(10/11), ln(1.1), ln(10/11)
        // — anti-correlated requires SAME returns inverted, so use B = 100 -> 100/1.1 -> 100 ->
        // 100/1.1.
        stubSeries(
                a,
                List.of(
                        price(a, LocalDate.of(2026, 1, 1), "100"),
                        price(a, LocalDate.of(2026, 1, 2), "110"),
                        price(a, LocalDate.of(2026, 1, 3), "100"),
                        price(a, LocalDate.of(2026, 1, 4), "110")));
        stubSeries(
                b,
                List.of(
                        price(b, LocalDate.of(2026, 1, 1), "100"),
                        price(b, LocalDate.of(2026, 1, 2), "90.909090909"),
                        price(b, LocalDate.of(2026, 1, 3), "100"),
                        price(b, LocalDate.of(2026, 1, 4), "90.909090909")));

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.PEARSON);

        Double offDiag = res.matrix().get(0).get(1);
        assertThat(offDiag).isNotNull();
        assertThat(offDiag).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void zeroStddevPairCellSurfacesAsNull() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        stubAsset(c, "C");
        // A varies; B is constant (stddev = 0).
        stubSeries(
                a,
                List.of(
                        price(a, LocalDate.of(2026, 1, 1), "100"),
                        price(a, LocalDate.of(2026, 1, 2), "110"),
                        price(a, LocalDate.of(2026, 1, 3), "120")));
        stubSeries(
                b,
                List.of(
                        price(b, LocalDate.of(2026, 1, 1), "50"),
                        price(b, LocalDate.of(2026, 1, 2), "50"),
                        price(b, LocalDate.of(2026, 1, 3), "50")));
        stubSeries(
                c,
                List.of(
                        price(c, LocalDate.of(2026, 1, 1), "10"),
                        price(c, LocalDate.of(2026, 1, 2), "11"),
                        price(c, LocalDate.of(2026, 1, 3), "12.1")));

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b, c), null, null, CorrelationMethod.PEARSON);

        // A vs B: B has zero log-return variance -> cell is null.
        assertThat(res.matrix().get(0).get(1)).isNull();
        // A vs C: both vary -> finite value.
        assertThat(res.matrix().get(0).get(2)).isNotNull();
    }

    @Test
    void sparseAlignmentTakesIntersectionOnly() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        // A has Mon-Tue-Wed-Thu-Fri prices.
        stubSeries(
                a,
                List.of(
                        price(a, LocalDate.of(2026, 1, 5), "100"),
                        price(a, LocalDate.of(2026, 1, 6), "110"),
                        price(a, LocalDate.of(2026, 1, 7), "120"),
                        price(a, LocalDate.of(2026, 1, 8), "115"),
                        price(a, LocalDate.of(2026, 1, 9), "125")));
        // B has only Mon-Wed-Fri prices.
        stubSeries(
                b,
                List.of(
                        price(b, LocalDate.of(2026, 1, 5), "200"),
                        price(b, LocalDate.of(2026, 1, 7), "210"),
                        price(b, LocalDate.of(2026, 1, 9), "215")));

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.PEARSON);

        // Intersection = {Mon, Wed, Fri} = 3 dates -> 2 returns.
        assertThat(res.dataPoints().get(0).get(1)).isEqualTo(2);
        // alignedDays = global N-way intersection size = 3.
        assertThat(res.samplePeriod().alignedDays()).isEqualTo(3);
    }

    @Test
    void duplicateAssetIdsAreDeduplicated() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        stubSeries(a, List.of());
        stubSeries(b, List.of());

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b, a, b), null, null, CorrelationMethod.PEARSON);

        assertThat(res.assetIds()).containsExactly(a, b);
        assertThat(res.matrix()).hasSize(2);
    }

    @Test
    void maxAssetCapEnforced() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 26; i++) ids.add(UUID.randomUUID());

        assertThatThrownBy(
                        () -> service.compute(userId, ids, null, null, CorrelationMethod.PEARSON))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Too many assets");
    }

    @Test
    void fromAfterToRejected() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                service.compute(
                                        userId,
                                        List.of(a, b),
                                        LocalDate.of(2026, 5, 1),
                                        LocalDate.of(2026, 1, 1),
                                        CorrelationMethod.PEARSON))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid range");
    }

    @Test
    void unknownAssetIdSurfacesAsResourceNotFound() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        when(assetRepository.findById(b)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.compute(
                                        userId,
                                        List.of(a, b),
                                        null,
                                        null,
                                        CorrelationMethod.PEARSON))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(b.toString());
    }

    @Test
    void defaultRangeWindowsLast90DaysWhenBothAbsent() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        stubSeries(a, List.of());
        stubSeries(b, List.of());

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.PEARSON);

        long windowDays =
                java.time.temporal.ChronoUnit.DAYS.between(
                        res.samplePeriod().from(), res.samplePeriod().to());
        assertThat(windowDays).isEqualTo(CorrelationService.MAX_WINDOW_DAYS);
    }

    @Test
    void spearmanDiffersFromPearsonOnMonotonicNonLinearSeries() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        // Construct return-level monotonic-but-non-linear relation:
        // A returns: 0.01, 0.02, 0.03, 0.04
        // B returns: f(A) where f is monotonic but quadratic — sqrt(A) preserves rank order.
        // Build prices that produce those returns: p_t = p_{t-1} * exp(r_t).
        double[] retA = {0.01, 0.02, 0.03, 0.04};
        double[] retB = {
            0.01, 0.04, 0.09, 0.16
        }; // monotonic, quadratic — Spearman = 1, Pearson < 1.
        List<PriceHistory> aSeries = new ArrayList<>();
        List<PriceHistory> bSeries = new ArrayList<>();
        double pA = 100.0;
        double pB = 100.0;
        aSeries.add(price(a, LocalDate.of(2026, 1, 1), String.valueOf(pA)));
        bSeries.add(price(b, LocalDate.of(2026, 1, 1), String.valueOf(pB)));
        for (int t = 0; t < retA.length; t++) {
            pA *= Math.exp(retA[t]);
            pB *= Math.exp(retB[t]);
            aSeries.add(price(a, LocalDate.of(2026, 1, 2 + t), String.valueOf(pA)));
            bSeries.add(price(b, LocalDate.of(2026, 1, 2 + t), String.valueOf(pB)));
        }
        stubSeries(a, aSeries);
        stubSeries(b, bSeries);

        CorrelationMatrixResponse pearson =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.PEARSON);
        CorrelationMatrixResponse spearman =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.SPEARMAN);

        Double rPearson = pearson.matrix().get(0).get(1);
        Double rSpearman = spearman.matrix().get(0).get(1);

        assertThat(rPearson).isNotNull();
        assertThat(rSpearman).isNotNull();
        // Spearman captures monotonicity exactly: rank correlation = 1.
        assertThat(rSpearman).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        // Pearson is < 1 because the relationship is nonlinear.
        assertThat(rPearson).isLessThan(0.99);
        // Method echoed in response.
        assertThat(spearman.method()).isEqualTo("SPEARMAN");
        assertThat(pearson.method()).isEqualTo("PEARSON");
    }

    @Test
    void insufficientOverlapEmitsNullCellWithZeroDataPoints() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        // Only ONE overlapping date -> 0 returns after differencing.
        stubSeries(
                a,
                List.of(
                        price(a, LocalDate.of(2026, 1, 1), "100"),
                        price(a, LocalDate.of(2026, 1, 2), "110")));
        stubSeries(b, List.of(price(b, LocalDate.of(2026, 1, 1), "50")));

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.PEARSON);

        assertThat(res.matrix().get(0).get(1)).isNull();
        assertThat(res.dataPoints().get(0).get(1)).isEqualTo(0);
    }

    @Test
    void multipleIntradayRowsCollapseToLatestPerDate() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        stubAsset(a, "A");
        stubAsset(b, "B");
        Instant day1Morning = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant day1Afternoon = day1Morning.plusSeconds(12 * 3600);
        Instant day2 = day1Morning.plusSeconds(24 * 3600);
        // Two intraday rows on day 1; the kernel must keep the LATER one (110, not 100).
        stubSeries(
                a,
                List.of(
                        PriceHistory.builder()
                                .assetId(a)
                                .price(new BigDecimal("100"))
                                .recordedAt(day1Morning)
                                .build(),
                        PriceHistory.builder()
                                .assetId(a)
                                .price(new BigDecimal("110"))
                                .recordedAt(day1Afternoon)
                                .build(),
                        PriceHistory.builder()
                                .assetId(a)
                                .price(new BigDecimal("121"))
                                .recordedAt(day2)
                                .build()));
        stubSeries(
                b,
                List.of(
                        PriceHistory.builder()
                                .assetId(b)
                                .price(new BigDecimal("200"))
                                .recordedAt(day1Morning)
                                .build(),
                        PriceHistory.builder()
                                .assetId(b)
                                .price(new BigDecimal("220"))
                                .recordedAt(day1Afternoon)
                                .build(),
                        PriceHistory.builder()
                                .assetId(b)
                                .price(new BigDecimal("242"))
                                .recordedAt(day2)
                                .build()));

        CorrelationMatrixResponse res =
                service.compute(userId, List.of(a, b), null, null, CorrelationMethod.PEARSON);

        // 2 dates -> 1 return; 1 return is "insufficient" by the n>=2 guard -> null.
        assertThat(res.dataPoints().get(0).get(1)).isEqualTo(1);
        assertThat(res.matrix().get(0).get(1)).isNull();
    }
}
