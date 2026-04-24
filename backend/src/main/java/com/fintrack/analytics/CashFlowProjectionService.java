package com.fintrack.analytics;

import com.fintrack.analytics.dto.CashFlowProjectionResponse;
import com.fintrack.analytics.dto.CashFlowProjectionResponse.MonthPoint;
import com.fintrack.analytics.dto.CashFlowProjectionResponse.ScheduledItem;
import com.fintrack.bills.BillRepository;
import com.fintrack.budget.MonthlySummaryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.budget.recurring.RecurringTemplateRepository;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.MonthlySummary;
import com.fintrack.common.entity.RecurringTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashFlowProjectionService {

    private static final int SAMPLE_MONTHS = 12;
    private static final int MIN_SAMPLES = 3;
    private static final int MIN_HORIZON = 1;
    private static final int MAX_HORIZON = 24;

    private final MonthlySummaryRepository summaryRepo;
    private final TransactionRepository txnRepo;
    private final RecurringTemplateRepository recurringRepo;
    private final BillRepository billRepo;

    @Transactional(readOnly = true)
    public CashFlowProjectionResponse project(
            UUID userId, Integer horizonMonths, BigDecimal startingBalance) {
        int horizon = clampHorizon(horizonMonths);
        BigDecimal start = startingBalance != null ? startingBalance : BigDecimal.ZERO;

        Averages avg = averagesFromSummaries(userId);
        if (avg == null) avg = averagesFromTransactions(userId);
        BigDecimal avgIncome = avg.income;
        BigDecimal avgExpense = avg.expense;
        BigDecimal avgNet = avgIncome.subtract(avgExpense);

        List<RecurringTemplate> templates =
                recurringRepo.findByUserIdOrderByCreatedAtAsc(userId).stream()
                        .filter(RecurringTemplate::isActive)
                        .toList();
        List<Bill> bills = billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId);

        YearMonth cursor = YearMonth.now().plusMonths(1);
        BigDecimal balance = start;
        List<MonthPoint> points = new ArrayList<>(horizon);

        for (int i = 0; i < horizon; i++) {
            YearMonth ym = cursor.plusMonths(i);
            BigDecimal scheduledIncome = BigDecimal.ZERO;
            BigDecimal scheduledExpense = BigDecimal.ZERO;
            List<ScheduledItem> items = new ArrayList<>();

            for (RecurringTemplate t : templates) {
                ScheduledItem item =
                        new ScheduledItem(
                                "recurring",
                                t.getDescription() != null && !t.getDescription().isBlank()
                                        ? t.getDescription()
                                        : "Recurring",
                                t.getTxnType().name(),
                                t.getAmount());
                items.add(item);
                if (t.getTxnType() == BudgetTransaction.TxnType.INCOME) {
                    scheduledIncome = scheduledIncome.add(t.getAmount());
                } else {
                    scheduledExpense = scheduledExpense.add(t.getAmount());
                }
            }
            for (Bill b : bills) {
                items.add(new ScheduledItem("bill", b.getName(), "EXPENSE", b.getAmount()));
                scheduledExpense = scheduledExpense.add(b.getAmount());
            }

            BigDecimal projIncome = avgIncome.max(scheduledIncome);
            BigDecimal projExpense = avgExpense.max(scheduledExpense);
            BigDecimal net = projIncome.subtract(projExpense);
            balance = balance.add(net);

            points.add(
                    new MonthPoint(
                            ym.toString(),
                            round(projIncome),
                            round(projExpense),
                            round(net),
                            round(balance),
                            round(scheduledIncome),
                            round(scheduledExpense),
                            items));
        }

        return new CashFlowProjectionResponse(
                round(avgIncome),
                round(avgExpense),
                round(avgNet),
                avg.samples,
                avg.samples >= MIN_SAMPLES,
                round(start),
                points);
    }

    private record Averages(BigDecimal income, BigDecimal expense, int samples) {}

    private Averages averagesFromSummaries(UUID userId) {
        List<MonthlySummary> summaries = summaryRepo.findByUserIdOrderByPeriodDesc(userId);
        if (summaries.size() < MIN_SAMPLES) return null;
        List<MonthlySummary> window =
                summaries.subList(0, Math.min(SAMPLE_MONTHS, summaries.size()));
        BigDecimal incomeSum = BigDecimal.ZERO;
        BigDecimal expenseSum = BigDecimal.ZERO;
        for (MonthlySummary s : window) {
            incomeSum = incomeSum.add(nvl(s.getTotalIncome()));
            expenseSum = expenseSum.add(nvl(s.getTotalExpense()));
        }
        int n = window.size();
        return new Averages(
                incomeSum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP),
                expenseSum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP),
                n);
    }

    private Averages averagesFromTransactions(UUID userId) {
        YearMonth current = YearMonth.now();
        BigDecimal incomeSum = BigDecimal.ZERO;
        BigDecimal expenseSum = BigDecimal.ZERO;
        int samples = 0;
        for (int i = 0; i < SAMPLE_MONTHS; i++) {
            YearMonth ym = current.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            BigDecimal inc =
                    nvl(
                            txnRepo.sumByUserIdAndTypeAndDateRange(
                                    userId, BudgetTransaction.TxnType.INCOME, from, to));
            BigDecimal exp =
                    nvl(
                            txnRepo.sumByUserIdAndTypeAndDateRange(
                                    userId, BudgetTransaction.TxnType.EXPENSE, from, to));
            if (inc.signum() == 0 && exp.signum() == 0) continue;
            incomeSum = incomeSum.add(inc);
            expenseSum = expenseSum.add(exp);
            samples++;
        }
        if (samples == 0) return new Averages(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        return new Averages(
                incomeSum.divide(BigDecimal.valueOf(samples), 2, RoundingMode.HALF_UP),
                expenseSum.divide(BigDecimal.valueOf(samples), 2, RoundingMode.HALF_UP),
                samples);
    }

    private static int clampHorizon(Integer v) {
        if (v == null) return 12;
        return Math.max(MIN_HORIZON, Math.min(MAX_HORIZON, v));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
