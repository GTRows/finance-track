package com.fintrack.budget;

import com.fintrack.budget.dto.BudgetSummaryResponse;
import com.fintrack.budget.rule.TransactionCategoryRuleService;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.price.FxConversionService;
import com.fintrack.settings.UserSettingsRepository;
import com.fintrack.tag.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock TransactionRepository txnRepo;
    @Mock IncomeCategoryRepository incomeRepo;
    @Mock ExpenseCategoryRepository expenseRepo;
    @Mock MonthlySummaryRepository summaryRepo;
    @Mock BudgetRuleService budgetRuleService;
    @Mock TransactionCategoryRuleService categoryRuleService;
    @Mock TagService tagService;
    @Mock UserSettingsRepository userSettingsRepo;
    @Mock FxConversionService fxConversionService;

    @InjectMocks BudgetService service;

    private final UUID userId = UUID.randomUUID();

    private BudgetTransaction txn(TxnType type, String amount, UUID categoryId) {
        return BudgetTransaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .txnType(type)
                .amount(new BigDecimal(amount))
                .currency("TRY")
                .categoryId(categoryId)
                .txnDate(LocalDate.of(2026, 4, 15))
                .build();
    }

    @Test
    void summaryComputesNetAndSavingsRate() {
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(new BigDecimal("10000"));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("7500"));
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(eq(userId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        BudgetSummaryResponse res = service.summary(userId, "2026-04");

        assertThat(res.totalIncome()).isEqualByComparingTo("10000");
        assertThat(res.totalExpense()).isEqualByComparingTo("7500");
        assertThat(res.net()).isEqualByComparingTo("2500");
        assertThat(res.savingsRate()).isEqualByComparingTo("25.00");
    }

    @Test
    void summarySavingsRateIsZeroWhenNoIncome() {
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("500"));
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(eq(userId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        BudgetSummaryResponse res = service.summary(userId, "2026-04");

        assertThat(res.net()).isEqualByComparingTo("-500");
        assertThat(res.savingsRate()).isEqualByComparingTo("0");
    }

    @Test
    void summaryNegativeNetProducesNegativeSavingsRate() {
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(new BigDecimal("1000"));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1200"));
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(eq(userId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        BudgetSummaryResponse res = service.summary(userId, "2026-04");

        assertThat(res.savingsRate()).isEqualByComparingTo("-20.00");
    }

    @Test
    void summaryGroupsIncomeByCategoryWithPercent() {
        UUID salaryCatId = UUID.randomUUID();
        UUID sideCatId = UUID.randomUUID();
        IncomeCategory salary = new IncomeCategory();
        salary.setId(salaryCatId);
        salary.setUserId(userId);
        salary.setName("Salary");
        salary.setColor("#aabbcc");
        IncomeCategory side = new IncomeCategory();
        side.setId(sideCatId);
        side.setUserId(userId);
        side.setName("Side");
        side.setColor("#ddeeff");

        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(new BigDecimal("1000"));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(salary, side));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(eq(userId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(
                        txn(TxnType.INCOME, "800", salaryCatId),
                        txn(TxnType.INCOME, "200", sideCatId)
                )));

        BudgetSummaryResponse res = service.summary(userId, "2026-04");

        assertThat(res.incomeByCategory()).hasSize(2);
        assertThat(res.incomeByCategory().get(0).amount()).isEqualByComparingTo("800");
        assertThat(res.incomeByCategory().get(0).percent()).isEqualByComparingTo("80.00");
        assertThat(res.incomeByCategory().get(1).amount()).isEqualByComparingTo("200");
        assertThat(res.incomeByCategory().get(1).percent()).isEqualByComparingTo("20.00");
    }

    @Test
    void summaryGroupsExpenseWithNullCategoryAsUncategorized() {
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("300"));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(eq(userId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(txn(TxnType.EXPENSE, "300", null))));

        BudgetSummaryResponse res = service.summary(userId, "2026-04");

        assertThat(res.expenseByCategory()).hasSize(1);
        assertThat(res.expenseByCategory().get(0).categoryId()).isNull();
        assertThat(res.expenseByCategory().get(0).categoryName()).isEqualTo("Uncategorized");
    }

    @Test
    void summaryAccumulatesRolloverFromPriorMonths() {
        UUID catId = UUID.randomUUID();
        ExpenseCategory food = ExpenseCategory.builder()
                .id(catId).userId(userId).name("Food").color("#f00")
                .budgetAmount(new BigDecimal("500"))
                .rolloverEnabled(true).build();

        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("400"));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(food));
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(eq(userId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(txn(TxnType.EXPENSE, "400", catId))));

        when(txnRepo.sumByUserIdAndCategoryAndDateRange(eq(userId), eq(catId),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(new BigDecimal("300"));
        when(txnRepo.sumByUserIdAndCategoryAndDateRange(eq(userId), eq(catId),
                eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28))))
                .thenReturn(new BigDecimal("200"));
        when(txnRepo.sumByUserIdAndCategoryAndDateRange(eq(userId), eq(catId),
                eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31))))
                .thenReturn(new BigDecimal("500"));

        BudgetSummaryResponse res = service.summary(userId, "2026-04");

        BudgetSummaryResponse.CategoryAmount foodRow = res.expenseByCategory().stream()
                .filter(c -> catId.equals(c.categoryId()))
                .findFirst().orElseThrow();
        assertThat(foodRow.baseBudget()).isEqualByComparingTo("500");
        assertThat(foodRow.rolloverAmount()).isEqualByComparingTo("500");
        assertThat(foodRow.effectiveBudget()).isEqualByComparingTo("1000");
    }

    @Test
    void summaryOverspendResetsRolloverToZero() {
        UUID catId = UUID.randomUUID();
        ExpenseCategory food = ExpenseCategory.builder()
                .id(catId).userId(userId).name("Food").color("#f00")
                .budgetAmount(new BigDecimal("500"))
                .rolloverEnabled(true).build();

        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(food));
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(eq(userId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(txnRepo.sumByUserIdAndCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("900"));

        BudgetSummaryResponse res = service.summary(userId, "2026-03");

        BudgetSummaryResponse.CategoryAmount foodRow = res.expenseByCategory().stream()
                .filter(c -> catId.equals(c.categoryId()))
                .findFirst().orElseThrow();
        assertThat(foodRow.rolloverAmount()).isEqualByComparingTo("0");
        assertThat(foodRow.effectiveBudget()).isEqualByComparingTo("500");
    }
}
