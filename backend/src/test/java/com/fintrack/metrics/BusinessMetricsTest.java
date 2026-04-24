package com.fintrack.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessMetricsTest {

    @Mock SnapshotRepository snapshotRepository;
    @Mock TransactionRepository transactionRepository;

    private SimpleMeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(registry, snapshotRepository, transactionRepository);
        metrics.registerGauges();
    }

    @Test
    void registerGaugesCreatesAllFourMeters() {
        assertThat(registry.find("fintrack.portfolio.total.value.try").gauge()).isNotNull();
        assertThat(registry.find("fintrack.budget.month.income.try").gauge()).isNotNull();
        assertThat(registry.find("fintrack.budget.month.expense.try").gauge()).isNotNull();
        assertThat(registry.find("fintrack.budget.transactions.today").gauge()).isNotNull();
    }

    @Test
    void refreshPublishesDatabaseValuesToGauges() {
        when(snapshotRepository.sumLatestTotalValueTry()).thenReturn(new BigDecimal("12345.67"));
        when(transactionRepository.sumByTypeAndDateRange(
                        eq(TxnType.INCOME), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("10000"));
        when(transactionRepository.sumByTypeAndDateRange(
                        eq(TxnType.EXPENSE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("4500"));
        when(transactionRepository.countByTxnDate(any(LocalDate.class))).thenReturn(7L);

        metrics.refresh();

        Gauge pf = registry.find("fintrack.portfolio.total.value.try").gauge();
        Gauge inc = registry.find("fintrack.budget.month.income.try").gauge();
        Gauge exp = registry.find("fintrack.budget.month.expense.try").gauge();
        Gauge cnt = registry.find("fintrack.budget.transactions.today").gauge();
        assertThat(pf.value()).isEqualTo(12345.67);
        assertThat(inc.value()).isEqualTo(10000.0);
        assertThat(exp.value()).isEqualTo(4500.0);
        assertThat(cnt.value()).isEqualTo(7.0);
    }

    @Test
    void refreshTreatsNullSumsAsZero() {
        when(snapshotRepository.sumLatestTotalValueTry()).thenReturn(null);
        when(transactionRepository.sumByTypeAndDateRange(any(), any(), any())).thenReturn(null);
        when(transactionRepository.countByTxnDate(any(LocalDate.class))).thenReturn(0L);

        metrics.refresh();

        assertThat(registry.find("fintrack.portfolio.total.value.try").gauge().value()).isZero();
        assertThat(registry.find("fintrack.budget.month.income.try").gauge().value()).isZero();
    }

    @Test
    void refreshSwallowsRepositoryExceptions() {
        when(snapshotRepository.sumLatestTotalValueTry()).thenThrow(new RuntimeException("boom"));

        metrics.refresh();

        // Gauge remains at the initial 0.0 rather than propagating the failure.
        assertThat(registry.find("fintrack.portfolio.total.value.try").gauge().value()).isZero();
    }

    @Test
    void recordAlertFiredCreatesCounterTaggedBySource() {
        metrics.recordAlertFired("price");

        Counter counter = registry.find("fintrack.alerts.fired").tag("source", "price").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordAlertFiredIncrementsExistingCounter() {
        metrics.recordAlertFired("budget");
        metrics.recordAlertFired("budget");
        metrics.recordAlertFired("budget");

        Counter counter = registry.find("fintrack.alerts.fired").tag("source", "budget").counter();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    void recordAlertFiredSplitsSourcesIntoSeparateCounters() {
        metrics.recordAlertFired("price");
        metrics.recordAlertFired("budget");
        metrics.recordAlertFired("price");

        Counter price = registry.find("fintrack.alerts.fired").tag("source", "price").counter();
        Counter budget = registry.find("fintrack.alerts.fired").tag("source", "budget").counter();
        assertThat(price.count()).isEqualTo(2.0);
        assertThat(budget.count()).isEqualTo(1.0);
    }
}
