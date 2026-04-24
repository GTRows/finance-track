package com.fintrack.portfolio.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.risk.dto.RiskMetricsResponse;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class RiskServiceTest {

    @Mock SnapshotRepository snapshotRepository;
    @Mock PortfolioRepository portfolioRepository;

    @InjectMocks RiskService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    private void stubOwnership() {
        Portfolio p =
                Portfolio.builder().id(portfolioId).userId(userId).name("P").active(true).build();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(p));
    }

    private PortfolioSnapshot snap(LocalDate date, String value) {
        return PortfolioSnapshot.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .snapshotDate(date)
                .totalValueTry(new BigDecimal(value))
                .totalCostTry(BigDecimal.ZERO)
                .build();
    }

    private List<PortfolioSnapshot> flatSnapshots(int count, String value) {
        List<PortfolioSnapshot> out = new ArrayList<>(count);
        LocalDate day = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            out.add(snap(day.plusDays(i), value));
        }
        return out;
    }

    @Test
    void computeThrowsWhenPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compute(userId, portfolioId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void returnsInsufficientWhenNoSnapshots() {
        stubOwnership();
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(List.of());

        RiskMetricsResponse res = service.compute(userId, portfolioId, null);

        assertThat(res.sufficientData()).isFalse();
        assertThat(res.snapshotCount()).isZero();
        assertThat(res.periodStart()).isNull();
        assertThat(res.periodEnd()).isNull();
        assertThat(res.totalReturn()).isNull();
        assertThat(res.riskFreeRate()).isEqualByComparingTo("0");
    }

    @Test
    void returnsInsufficientWithPeriodWhenTooFewSnapshots() {
        stubOwnership();
        List<PortfolioSnapshot> snaps = flatSnapshots(5, "1000");
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(snaps);

        RiskMetricsResponse res = service.compute(userId, portfolioId, new BigDecimal("0.05"));

        assertThat(res.sufficientData()).isFalse();
        assertThat(res.snapshotCount()).isEqualTo(5);
        assertThat(res.periodStart()).isEqualTo(snaps.get(0).getSnapshotDate());
        assertThat(res.periodEnd()).isEqualTo(snaps.get(4).getSnapshotDate());
        assertThat(res.riskFreeRate()).isEqualByComparingTo("0.05");
    }

    @Test
    void zeroVolatilitySeriesYieldsZeroSharpeAndZeroDrawdown() {
        stubOwnership();
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(flatSnapshots(25, "1000"));

        RiskMetricsResponse res = service.compute(userId, portfolioId, new BigDecimal("0.0"));

        assertThat(res.sufficientData()).isTrue();
        assertThat(res.totalReturn()).isEqualByComparingTo("0");
        assertThat(res.annualVolatility()).isEqualByComparingTo("0");
        assertThat(res.sharpeRatio()).isEqualByComparingTo("0");
        assertThat(res.maxDrawdown()).isEqualByComparingTo("0");
        assertThat(res.bestDay()).isEqualByComparingTo("0");
        assertThat(res.worstDay()).isEqualByComparingTo("0");
    }

    @Test
    void totalReturnIsLastDividedByFirstMinusOne() {
        stubOwnership();
        List<PortfolioSnapshot> snaps = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 1, 1);
        snaps.add(snap(day, "1000"));
        for (int i = 1; i < 24; i++) snaps.add(snap(day.plusDays(i), "1100"));
        snaps.add(snap(day.plusDays(24), "1200"));
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(snaps);

        RiskMetricsResponse res = service.compute(userId, portfolioId, null);

        assertThat(res.totalReturn()).isEqualByComparingTo("0.200000");
    }

    @Test
    void bestAndWorstDayComeFromDailyReturnSet() {
        stubOwnership();
        List<PortfolioSnapshot> snaps = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        String[] values = new String[30];
        for (int i = 0; i < 30; i++) values[i] = "1000";
        values[5] = "1100";
        values[6] = "990";
        values[10] = "1200";
        values[11] = "1100";
        values[15] = "1050";
        values[16] = "800";
        for (int i = 0; i < values.length; i++) snaps.add(snap(d.plusDays(i), values[i]));
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(snaps);

        RiskMetricsResponse res = service.compute(userId, portfolioId, null);

        assertThat(res.sufficientData()).isTrue();
        assertThat(res.bestDay()).isGreaterThan(new BigDecimal("0.09"));
        assertThat(res.worstDay()).isLessThan(new BigDecimal("-0.2"));
    }

    @Test
    void maxDrawdownIsNegativeForPeakThenDecline() {
        stubOwnership();
        List<PortfolioSnapshot> snaps = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        BigDecimal[] values = new BigDecimal[40];
        for (int i = 0; i < 20; i++) values[i] = BigDecimal.valueOf(1000 + i * 10);
        BigDecimal peakVal = values[19];
        for (int i = 20; i < 40; i++)
            values[i] = peakVal.subtract(BigDecimal.valueOf((i - 19) * 20));
        for (int i = 0; i < values.length; i++)
            snaps.add(snap(d.plusDays(i), values[i].toPlainString()));

        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(snaps);

        RiskMetricsResponse res = service.compute(userId, portfolioId, null);

        assertThat(res.maxDrawdown()).isNegative();
    }

    @Test
    void skipsReturnsWherePreviousValueIsNullOrZero() {
        stubOwnership();
        List<PortfolioSnapshot> snaps = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 22; i++) snaps.add(snap(d.plusDays(i), "1000"));
        snaps.set(
                3,
                PortfolioSnapshot.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .snapshotDate(d.plusDays(3))
                        .totalValueTry(BigDecimal.ZERO)
                        .totalCostTry(BigDecimal.ZERO)
                        .build());
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(snaps);

        RiskMetricsResponse res = service.compute(userId, portfolioId, null);

        assertThat(res.sufficientData()).isTrue();
    }

    @Test
    void riskFreeRateDefaultsToZeroWhenOmitted() {
        stubOwnership();
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(flatSnapshots(25, "1000"));

        RiskMetricsResponse res = service.compute(userId, portfolioId, null);

        assertThat(res.riskFreeRate()).isEqualByComparingTo("0");
    }

    @Test
    void nonZeroReturnsProduceNonZeroVolatility() {
        stubOwnership();
        List<PortfolioSnapshot> snaps = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 30; i++) {
            int v = i % 2 == 0 ? 1000 : 1050;
            snaps.add(snap(d.plusDays(i), Integer.toString(v)));
        }
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId))
                .thenReturn(snaps);

        RiskMetricsResponse res = service.compute(userId, portfolioId, new BigDecimal("0.00"));

        assertThat(res.sufficientData()).isTrue();
        assertThat(res.annualVolatility()).isPositive();
    }
}
