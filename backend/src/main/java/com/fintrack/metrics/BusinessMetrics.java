package com.fintrack.metrics;

import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Publishes FinTrack business metrics on the same Micrometer registry that
 * backs the /actuator/prometheus endpoint. Gauges refresh every 60s; the
 * alert counter is incremented from the notification-producing services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessMetrics {

    private final MeterRegistry registry;
    private final SnapshotRepository snapshotRepository;
    private final TransactionRepository transactionRepository;

    private final AtomicReference<Double> portfolioTotalValueTry = new AtomicReference<>(0.0);
    private final AtomicReference<Double> monthIncomeTry = new AtomicReference<>(0.0);
    private final AtomicReference<Double> monthExpenseTry = new AtomicReference<>(0.0);
    private final AtomicLong transactionsToday = new AtomicLong(0L);

    private final ConcurrentHashMap<String, Counter> alertCounters = new ConcurrentHashMap<>();

    @PostConstruct
    void registerGauges() {
        registry.gauge("fintrack.portfolio.total.value.try",
                portfolioTotalValueTry, AtomicReference::get);
        registry.gauge("fintrack.budget.month.income.try",
                monthIncomeTry, AtomicReference::get);
        registry.gauge("fintrack.budget.month.expense.try",
                monthExpenseTry, AtomicReference::get);
        registry.gauge("fintrack.budget.transactions.today",
                transactionsToday, AtomicLong::doubleValue);
    }

    /**
     * Recomputes gauge values from the database. Runs slightly after the
     * backend finishes starting so the first scrape already carries a value.
     */
    @Scheduled(fixedDelay = 60, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    @Transactional(readOnly = true)
    public void refresh() {
        try {
            portfolioTotalValueTry.set(toDouble(snapshotRepository.sumLatestTotalValueTry()));

            YearMonth ym = YearMonth.now();
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            monthIncomeTry.set(toDouble(
                    transactionRepository.sumByTypeAndDateRange(BudgetTransaction.TxnType.INCOME, from, to)));
            monthExpenseTry.set(toDouble(
                    transactionRepository.sumByTypeAndDateRange(BudgetTransaction.TxnType.EXPENSE, from, to)));

            transactionsToday.set(transactionRepository.countByTxnDate(LocalDate.now()));
        } catch (Exception ex) {
            log.warn("Business metrics refresh failed: {}", ex.getMessage());
        }
    }

    /** Bumps the alerts-fired counter, tagged by source (price, budget, ...). */
    public void recordAlertFired(String source) {
        alertCounters.computeIfAbsent(source, s ->
                Counter.builder("fintrack.alerts.fired")
                        .tag("source", s)
                        .description("Total alerts dispatched since process start")
                        .register(registry)
        ).increment();
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
