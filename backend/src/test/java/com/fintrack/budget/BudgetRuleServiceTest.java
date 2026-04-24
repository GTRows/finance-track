package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.alert.AlertNotificationRepository;
import com.fintrack.budget.dto.BudgetRuleResponse;
import com.fintrack.budget.dto.CreateBudgetRuleRequest;
import com.fintrack.common.entity.AlertNotification;
import com.fintrack.common.entity.BudgetRule;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.metrics.BusinessMetrics;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BudgetRuleServiceTest {

    @Mock BudgetRuleRepository ruleRepo;
    @Mock ExpenseCategoryRepository expenseRepo;
    @Mock TransactionRepository txnRepo;
    @Mock AlertNotificationRepository notificationRepo;
    @Mock BusinessMetrics businessMetrics;

    @InjectMocks BudgetRuleService service;

    private final UUID userId = UUID.randomUUID();

    private ExpenseCategory cat(String name, String color) {
        return ExpenseCategory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .color(color)
                .build();
    }

    private BudgetRule rule(UUID categoryId, String limit, boolean active, String lastAlerted) {
        return BudgetRule.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .categoryId(categoryId)
                .monthlyLimitTry(new BigDecimal(limit))
                .active(active)
                .lastAlertedPeriod(lastAlerted)
                .build();
    }

    private BudgetTransaction expense(UUID categoryId, String amount, LocalDate date) {
        return BudgetTransaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .txnType(TxnType.EXPENSE)
                .categoryId(categoryId)
                .amount(new BigDecimal(amount))
                .txnDate(date)
                .description("x")
                .build();
    }

    @Test
    void listEmptyWhenNoRules() {
        when(ruleRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        assertThat(service.listForUser(userId)).isEmpty();
    }

    @Test
    void listAttachesSpendAndPercent() {
        ExpenseCategory food = cat("Food", "#f00");
        BudgetRule r = rule(food.getId(), "1000", true, null);
        YearMonth ym = YearMonth.now();

        when(ruleRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(r));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(food));

        Page<BudgetTransaction> page =
                new PageImpl<>(
                        List.of(
                                expense(food.getId(), "250", ym.atDay(10)),
                                expense(food.getId(), "250", ym.atDay(15)),
                                expense(food.getId(), "100", ym.atDay(20))));
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                        eq(userId), eq(ym.atDay(1)), eq(ym.atEndOfMonth()), any(Pageable.class)))
                .thenReturn(page);

        BudgetRuleResponse res = service.listForUser(userId).get(0);

        assertThat(res.categoryName()).isEqualTo("Food");
        assertThat(res.monthlyLimitTry()).isEqualByComparingTo("1000");
        assertThat(res.currentSpendTry()).isEqualByComparingTo("600");
        assertThat(res.usagePct()).isEqualByComparingTo("60.0000");
    }

    @Test
    void listReturnsUnknownCategoryLabelWhenLookupMisses() {
        BudgetRule r = rule(UUID.randomUUID(), "1000", true, null);
        YearMonth ym = YearMonth.now();
        when(ruleRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(r));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                        eq(userId), eq(ym.atDay(1)), eq(ym.atEndOfMonth()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        BudgetRuleResponse res = service.listForUser(userId).get(0);

        assertThat(res.categoryName()).isEqualTo("Unknown");
        assertThat(res.categoryColor()).isNull();
        assertThat(res.currentSpendTry()).isEqualByComparingTo("0");
    }

    @Test
    void listZeroPercentWhenLimitZero() {
        ExpenseCategory food = cat("Food", null);
        BudgetRule r = rule(food.getId(), "0", true, null);
        YearMonth ym = YearMonth.now();
        when(ruleRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(r));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(food));
        when(txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                        eq(userId), eq(ym.atDay(1)), eq(ym.atEndOfMonth()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expense(food.getId(), "5", ym.atDay(1)))));

        BudgetRuleResponse res = service.listForUser(userId).get(0);

        assertThat(res.usagePct()).isEqualByComparingTo("0");
    }

    @Test
    void createRejectsUnknownCategory() {
        UUID cid = UUID.randomUUID();
        when(expenseRepo.findByIdAndUserId(cid, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        userId,
                                        new CreateBudgetRuleRequest(cid, new BigDecimal("500"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createInsertsNewRuleWhenNoneExistsForCategory() {
        ExpenseCategory food = cat("Food", "#f00");
        when(expenseRepo.findByIdAndUserId(food.getId(), userId)).thenReturn(Optional.of(food));
        when(ruleRepo.findByUserIdAndCategoryId(userId, food.getId())).thenReturn(Optional.empty());
        when(ruleRepo.save(any(BudgetRule.class)))
                .thenAnswer(
                        inv -> {
                            BudgetRule r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            return r;
                        });

        BudgetRuleResponse res =
                service.create(
                        userId, new CreateBudgetRuleRequest(food.getId(), new BigDecimal("500")));

        ArgumentCaptor<BudgetRule> captor = ArgumentCaptor.forClass(BudgetRule.class);
        verify(ruleRepo).save(captor.capture());
        BudgetRule saved = captor.getValue();
        assertThat(saved.getCategoryId()).isEqualTo(food.getId());
        assertThat(saved.getMonthlyLimitTry()).isEqualByComparingTo("500");
        assertThat(saved.isActive()).isTrue();
        assertThat(res.categoryName()).isEqualTo("Food");
    }

    @Test
    void createUpdatesExistingRuleForSameCategory() {
        ExpenseCategory food = cat("Food", "#f00");
        BudgetRule existing = rule(food.getId(), "300", false, null);
        when(expenseRepo.findByIdAndUserId(food.getId(), userId)).thenReturn(Optional.of(food));
        when(ruleRepo.findByUserIdAndCategoryId(userId, food.getId()))
                .thenReturn(Optional.of(existing));
        when(ruleRepo.save(existing)).thenReturn(existing);

        service.create(userId, new CreateBudgetRuleRequest(food.getId(), new BigDecimal("800")));

        assertThat(existing.getMonthlyLimitTry()).isEqualByComparingTo("800");
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void deleteRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(ruleRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ruleRepo, never()).delete(any());
    }

    @Test
    void deleteRemovesWhenOwned() {
        BudgetRule r = rule(UUID.randomUUID(), "1", true, null);
        when(ruleRepo.findByIdAndUserId(r.getId(), userId)).thenReturn(Optional.of(r));

        service.delete(userId, r.getId());

        verify(ruleRepo).delete(r);
    }

    @Test
    void evaluateIgnoresIncomeTransactions() {
        BudgetTransaction income =
                BudgetTransaction.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .txnType(TxnType.INCOME)
                        .categoryId(UUID.randomUUID())
                        .amount(new BigDecimal("100"))
                        .txnDate(LocalDate.now())
                        .build();

        service.evaluateForTransaction(userId, income);

        verify(ruleRepo, never()).findByUserIdAndCategoryId(any(), any());
    }

    @Test
    void evaluateIgnoresExpenseWithoutCategory() {
        BudgetTransaction txn =
                BudgetTransaction.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .txnType(TxnType.EXPENSE)
                        .amount(new BigDecimal("100"))
                        .txnDate(LocalDate.now())
                        .build();

        service.evaluateForTransaction(userId, txn);

        verify(ruleRepo, never()).findByUserIdAndCategoryId(any(), any());
    }

    @Test
    void evaluateExitsWhenNoRuleForCategory() {
        UUID catId = UUID.randomUUID();
        when(ruleRepo.findByUserIdAndCategoryId(userId, catId)).thenReturn(Optional.empty());

        service.evaluateForTransaction(userId, expense(catId, "10", LocalDate.now()));

        verify(notificationRepo, never()).save(any());
    }

    @Test
    void evaluateSkipsInactiveRule() {
        UUID catId = UUID.randomUUID();
        when(ruleRepo.findByUserIdAndCategoryId(userId, catId))
                .thenReturn(Optional.of(rule(catId, "100", false, null)));

        service.evaluateForTransaction(userId, expense(catId, "200", LocalDate.now()));

        verify(notificationRepo, never()).save(any());
    }

    @Test
    void evaluateSkipsWhenAlreadyAlertedThisPeriod() {
        UUID catId = UUID.randomUUID();
        YearMonth ym = YearMonth.now();
        when(ruleRepo.findByUserIdAndCategoryId(userId, catId))
                .thenReturn(Optional.of(rule(catId, "100", true, ym.toString())));

        service.evaluateForTransaction(userId, expense(catId, "500", ym.atDay(15)));

        verify(notificationRepo, never()).save(any());
    }

    @Test
    void evaluateSkipsWhenTotalSpendBelowLimit() {
        UUID catId = UUID.randomUUID();
        BudgetRule r = rule(catId, "1000", true, null);
        YearMonth ym = YearMonth.now();
        when(ruleRepo.findByUserIdAndCategoryId(userId, catId)).thenReturn(Optional.of(r));
        when(txnRepo.sumByUserIdAndCategoryAndDateRange(
                        eq(userId), eq(catId), eq(ym.atDay(1)), eq(ym.atEndOfMonth())))
                .thenReturn(new BigDecimal("500"));

        service.evaluateForTransaction(userId, expense(catId, "100", ym.atDay(10)));

        verify(notificationRepo, never()).save(any());
    }

    @Test
    void evaluateFiresAlertAndMarksPeriodWhenSpendMeetsLimit() {
        ExpenseCategory food = cat("Food", "#f00");
        BudgetRule r = rule(food.getId(), "1000", true, null);
        YearMonth ym = YearMonth.now();
        when(ruleRepo.findByUserIdAndCategoryId(userId, food.getId())).thenReturn(Optional.of(r));
        when(txnRepo.sumByUserIdAndCategoryAndDateRange(
                        eq(userId), eq(food.getId()), eq(ym.atDay(1)), eq(ym.atEndOfMonth())))
                .thenReturn(new BigDecimal("1200"));
        when(expenseRepo.findById(food.getId())).thenReturn(Optional.of(food));

        service.evaluateForTransaction(userId, expense(food.getId(), "300", ym.atDay(20)));

        ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
        verify(notificationRepo).save(captor.capture());
        AlertNotification n = captor.getValue();
        assertThat(n.getUserId()).isEqualTo(userId);
        assertThat(n.getSourceType()).isEqualTo(AlertNotification.SourceType.BUDGET_RULE);
        assertThat(n.getSourceId()).isEqualTo(r.getId());
        assertThat(n.getMessage()).contains("Food").contains("1200").contains("1000");
        assertThat(r.getLastAlertedPeriod()).isEqualTo(ym.toString());
        verify(businessMetrics).recordAlertFired("budget");
    }

    @Test
    void evaluateHandlesNullSumAsZero() {
        UUID catId = UUID.randomUUID();
        BudgetRule r = rule(catId, "1000", true, null);
        YearMonth ym = YearMonth.now();
        when(ruleRepo.findByUserIdAndCategoryId(userId, catId)).thenReturn(Optional.of(r));
        when(txnRepo.sumByUserIdAndCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(null);

        service.evaluateForTransaction(userId, expense(catId, "5", ym.atDay(15)));

        verify(notificationRepo, never()).save(any());
    }
}
