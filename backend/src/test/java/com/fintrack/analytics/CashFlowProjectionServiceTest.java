package com.fintrack.analytics;

import com.fintrack.analytics.dto.CashFlowProjectionResponse;
import com.fintrack.bills.BillRepository;
import com.fintrack.budget.MonthlySummaryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.budget.recurring.RecurringTemplateRepository;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.entity.MonthlySummary;
import com.fintrack.common.entity.RecurringTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowProjectionServiceTest {

    @Mock MonthlySummaryRepository summaryRepo;
    @Mock TransactionRepository txnRepo;
    @Mock RecurringTemplateRepository recurringRepo;
    @Mock BillRepository billRepo;

    @InjectMocks CashFlowProjectionService service;

    private final UUID userId = UUID.randomUUID();

    private MonthlySummary summary(String period, String income, String expense) {
        return MonthlySummary.builder()
                .id(UUID.randomUUID()).userId(userId).period(period)
                .totalIncome(new BigDecimal(income))
                .totalExpense(new BigDecimal(expense))
                .build();
    }

    private RecurringTemplate recurring(TxnType type, String amount, String description, boolean active) {
        return RecurringTemplate.builder()
                .id(UUID.randomUUID()).userId(userId)
                .txnType(type).amount(new BigDecimal(amount))
                .description(description).dayOfMonth(1).active(active)
                .build();
    }

    private Bill bill(String name, String amount, int dueDay) {
        return Bill.builder()
                .id(UUID.randomUUID()).userId(userId)
                .name(name).amount(new BigDecimal(amount))
                .dueDay(dueDay).active(true).build();
    }

    @Test
    void projectReturnsEmptyStateWhenNoData() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any())).thenReturn(null);
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        CashFlowProjectionResponse res = service.project(userId, null, null);

        assertThat(res.months()).hasSize(12);
        assertThat(res.avgMonthlyIncome()).isEqualByComparingTo("0");
        assertThat(res.avgMonthlyExpense()).isEqualByComparingTo("0");
        assertThat(res.avgMonthlyNet()).isEqualByComparingTo("0");
        assertThat(res.sufficient()).isFalse();
        assertThat(res.startingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void projectClampsHorizonBetween1And24() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any())).thenReturn(null);
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        assertThat(service.project(userId, 0, null).months()).hasSize(1);
        assertThat(service.project(userId, 100, null).months()).hasSize(24);
        assertThat(service.project(userId, 6, null).months()).hasSize(6);
    }

    @Test
    void firstPeriodIsNextMonth() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any())).thenReturn(null);
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        CashFlowProjectionResponse res = service.project(userId, 3, null);

        assertThat(res.months().get(0).period()).isEqualTo(YearMonth.now().plusMonths(1).toString());
        assertThat(res.months().get(2).period()).isEqualTo(YearMonth.now().plusMonths(3).toString());
    }

    @Test
    void usesSummaryAveragesAboveMinSamples() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of(
                summary("2026-03", "10000", "6000"),
                summary("2026-02", "10000", "6000"),
                summary("2026-01", "10000", "6000")));
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        CashFlowProjectionResponse res = service.project(userId, 1, new BigDecimal("500"));

        assertThat(res.avgMonthlyIncome()).isEqualByComparingTo("10000.00");
        assertThat(res.avgMonthlyExpense()).isEqualByComparingTo("6000.00");
        assertThat(res.sampleMonths()).isEqualTo(3);
        assertThat(res.sufficient()).isTrue();
        assertThat(res.months().get(0).endingBalance()).isEqualByComparingTo("4500.00");
    }

    @Test
    void fallsBackToTransactionsWhenSummariesBelowMinSamples() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of(
                summary("2026-03", "10000", "6000")));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(new BigDecimal("8000"));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("3000"));
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        CashFlowProjectionResponse res = service.project(userId, 2, BigDecimal.ZERO);

        assertThat(res.avgMonthlyIncome()).isEqualByComparingTo("8000.00");
        assertThat(res.avgMonthlyExpense()).isEqualByComparingTo("3000.00");
        assertThat(res.sampleMonths()).isEqualTo(12);
    }

    @Test
    void recurringAndBillsAppearAsScheduledItems() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of(
                summary("2026-03", "0", "0"),
                summary("2026-02", "0", "0"),
                summary("2026-01", "0", "0")));
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                recurring(TxnType.INCOME, "5000", "Salary", true),
                recurring(TxnType.EXPENSE, "1500", "Rent", true)));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of(
                bill("Internet", "200", 5),
                bill("Electric", "500", 12)));

        CashFlowProjectionResponse res = service.project(userId, 1, null);

        CashFlowProjectionResponse.MonthPoint m = res.months().get(0);
        assertThat(m.scheduled()).hasSize(4);
        assertThat(m.scheduledIncome()).isEqualByComparingTo("5000.00");
        assertThat(m.scheduledExpense()).isEqualByComparingTo("2200.00");
    }

    @Test
    void inactiveRecurringTemplatesAreSkipped() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any())).thenReturn(null);
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                recurring(TxnType.INCOME, "1000", "inactive", false)));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        CashFlowProjectionResponse res = service.project(userId, 1, null);

        assertThat(res.months().get(0).scheduled()).isEmpty();
        assertThat(res.months().get(0).scheduledIncome()).isEqualByComparingTo("0");
    }

    @Test
    void projectedIncomeIsMaxOfAverageAndScheduled() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of(
                summary("2026-03", "1000", "0"),
                summary("2026-02", "1000", "0"),
                summary("2026-01", "1000", "0")));
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                recurring(TxnType.INCOME, "7000", "Bonus", true)));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        CashFlowProjectionResponse res = service.project(userId, 1, null);

        assertThat(res.months().get(0).projectedIncome()).isEqualByComparingTo("7000.00");
    }

    @Test
    void projectedExpenseIsMaxOfAverageAndScheduled() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of(
                summary("2026-03", "0", "8000"),
                summary("2026-02", "0", "8000"),
                summary("2026-01", "0", "8000")));
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                recurring(TxnType.EXPENSE, "3000", "Subscription", true)));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of(
                bill("Electric", "500", 1)));

        CashFlowProjectionResponse res = service.project(userId, 1, null);

        assertThat(res.months().get(0).projectedExpense()).isEqualByComparingTo("8000.00");
    }

    @Test
    void balanceAccumulatesAcrossMonths() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of(
                summary("2026-03", "5000", "3000"),
                summary("2026-02", "5000", "3000"),
                summary("2026-01", "5000", "3000")));
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        CashFlowProjectionResponse res = service.project(userId, 3, new BigDecimal("1000"));

        assertThat(res.months().get(0).endingBalance()).isEqualByComparingTo("3000.00");
        assertThat(res.months().get(1).endingBalance()).isEqualByComparingTo("5000.00");
        assertThat(res.months().get(2).endingBalance()).isEqualByComparingTo("7000.00");
    }

    @Test
    void scheduledItemLabelFallsBackWhenDescriptionBlank() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any())).thenReturn(null);
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                RecurringTemplate.builder()
                        .id(UUID.randomUUID()).userId(userId)
                        .txnType(TxnType.INCOME).amount(new BigDecimal("100"))
                        .description("   ").dayOfMonth(1).active(true).build()));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        CashFlowProjectionResponse res = service.project(userId, 1, null);

        assertThat(res.months().get(0).scheduled().get(0).label()).isEqualTo("Recurring");
    }
}
