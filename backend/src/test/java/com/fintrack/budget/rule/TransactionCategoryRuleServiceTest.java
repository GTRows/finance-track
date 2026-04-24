package com.fintrack.budget.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.budget.rule.dto.CategoryRuleResponse;
import com.fintrack.budget.rule.dto.UpsertCategoryRuleRequest;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.entity.TransactionCategoryRule;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionCategoryRuleServiceTest {

    @Mock TransactionCategoryRuleRepository ruleRepo;
    @Mock IncomeCategoryRepository incomeRepo;
    @Mock ExpenseCategoryRepository expenseRepo;

    @InjectMocks TransactionCategoryRuleService service;

    private final UUID userId = UUID.randomUUID();

    private IncomeCategory incomeCat(String name, String color) {
        return IncomeCategory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .color(color)
                .build();
    }

    private ExpenseCategory expenseCat(String name, String color) {
        return ExpenseCategory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .color(color)
                .build();
    }

    private TransactionCategoryRule rule(
            String pattern, UUID categoryId, TxnType type, int priority) {
        return TransactionCategoryRule.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .pattern(pattern)
                .categoryId(categoryId)
                .txnType(type)
                .priority(priority)
                .matchCount(0)
                .build();
    }

    @Test
    void listReturnsEmptyWhenNoRules() {
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId)).thenReturn(List.of());

        assertThat(service.list(userId)).isEmpty();
        verify(incomeRepo, never()).findByUserIdOrderByNameAsc(any());
    }

    @Test
    void listAttachesCategoryNameAndColor() {
        IncomeCategory salary = incomeCat("Salary", "#0f0");
        ExpenseCategory food = expenseCat("Food", "#f00");
        TransactionCategoryRule r1 = rule("ACME", salary.getId(), TxnType.INCOME, 10);
        TransactionCategoryRule r2 = rule("MARKET", food.getId(), TxnType.EXPENSE, 20);

        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId))
                .thenReturn(List.of(r1, r2));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(salary));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(food));

        List<CategoryRuleResponse> res = service.list(userId);

        assertThat(res).hasSize(2);
        assertThat(res.get(0).categoryName()).isEqualTo("Salary");
        assertThat(res.get(0).categoryColor()).isEqualTo("#0f0");
        assertThat(res.get(1).categoryName()).isEqualTo("Food");
    }

    @Test
    void listFallsBackToUnknownWhenCategoryMissing() {
        TransactionCategoryRule r = rule("X", UUID.randomUUID(), TxnType.EXPENSE, 100);
        when(ruleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId)).thenReturn(List.of(r));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        List<CategoryRuleResponse> res = service.list(userId);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).categoryName()).isEqualTo("Unknown");
        assertThat(res.get(0).categoryColor()).isNull();
    }

    @Test
    void createTrimsPatternAndPersists() {
        ExpenseCategory food = expenseCat("Food", "#f00");
        when(expenseRepo.findByIdAndUserId(food.getId(), userId)).thenReturn(Optional.of(food));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(food));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(ruleRepo.save(any(TransactionCategoryRule.class)))
                .thenAnswer(
                        inv -> {
                            TransactionCategoryRule r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            return r;
                        });

        service.create(
                userId,
                new UpsertCategoryRuleRequest("  market  ", food.getId(), TxnType.EXPENSE, 50));

        ArgumentCaptor<TransactionCategoryRule> captor =
                ArgumentCaptor.forClass(TransactionCategoryRule.class);
        verify(ruleRepo).save(captor.capture());
        TransactionCategoryRule saved = captor.getValue();
        assertThat(saved.getPattern()).isEqualTo("market");
        assertThat(saved.getPriority()).isEqualTo(50);
        assertThat(saved.getTxnType()).isEqualTo(TxnType.EXPENSE);
    }

    @Test
    void createDefaultsPriorityTo100WhenNull() {
        IncomeCategory salary = incomeCat("Salary", "#0f0");
        when(incomeRepo.findByIdAndUserId(salary.getId(), userId)).thenReturn(Optional.of(salary));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(salary));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(ruleRepo.save(any(TransactionCategoryRule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.create(
                userId, new UpsertCategoryRuleRequest("x", salary.getId(), TxnType.INCOME, null));

        ArgumentCaptor<TransactionCategoryRule> captor =
                ArgumentCaptor.forClass(TransactionCategoryRule.class);
        verify(ruleRepo).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(100);
    }

    @Test
    void createThrowsWhenIncomeCategoryNotOwned() {
        UUID catId = UUID.randomUUID();
        when(incomeRepo.findByIdAndUserId(catId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        userId,
                                        new UpsertCategoryRuleRequest(
                                                "x", catId, TxnType.INCOME, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Income");

        verify(ruleRepo, never()).save(any());
    }

    @Test
    void createThrowsWhenExpenseCategoryNotOwned() {
        UUID catId = UUID.randomUUID();
        when(expenseRepo.findByIdAndUserId(catId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        userId,
                                        new UpsertCategoryRuleRequest(
                                                "x", catId, TxnType.EXPENSE, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Expense");
    }

    @Test
    void updateMutatesAllFields() {
        ExpenseCategory food = expenseCat("Food", "#f00");
        TransactionCategoryRule existing = rule("old", food.getId(), TxnType.EXPENSE, 100);
        when(ruleRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(expenseRepo.findByIdAndUserId(food.getId(), userId)).thenReturn(Optional.of(food));
        when(ruleRepo.save(existing)).thenReturn(existing);
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(food));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        service.update(
                userId,
                existing.getId(),
                new UpsertCategoryRuleRequest("  new  ", food.getId(), TxnType.EXPENSE, 5));

        assertThat(existing.getPattern()).isEqualTo("new");
        assertThat(existing.getPriority()).isEqualTo(5);
    }

    @Test
    void updateKeepsExistingPriorityWhenNullInRequest() {
        ExpenseCategory food = expenseCat("Food", "#f00");
        TransactionCategoryRule existing = rule("x", food.getId(), TxnType.EXPENSE, 42);
        when(ruleRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(expenseRepo.findByIdAndUserId(food.getId(), userId)).thenReturn(Optional.of(food));
        when(ruleRepo.save(existing)).thenReturn(existing);
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(food));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        service.update(
                userId,
                existing.getId(),
                new UpsertCategoryRuleRequest("y", food.getId(), TxnType.EXPENSE, null));

        assertThat(existing.getPriority()).isEqualTo(42);
    }

    @Test
    void updateThrowsWhenRuleNotOwned() {
        UUID id = UUID.randomUUID();
        when(ruleRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId,
                                        id,
                                        new UpsertCategoryRuleRequest(
                                                "x", UUID.randomUUID(), TxnType.EXPENSE, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
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
        TransactionCategoryRule r = rule("x", UUID.randomUUID(), TxnType.EXPENSE, 1);
        when(ruleRepo.findByIdAndUserId(r.getId(), userId)).thenReturn(Optional.of(r));

        service.delete(userId, r.getId());

        verify(ruleRepo).delete(r);
    }

    @Test
    void matchForReturnsEmptyWhenDescriptionNullOrBlank() {
        assertThat(service.matchFor(userId, TxnType.EXPENSE, null)).isEmpty();
        assertThat(service.matchFor(userId, TxnType.EXPENSE, "   ")).isEmpty();
        verify(ruleRepo, never())
                .findByUserIdAndTxnTypeOrderByPriorityAscCreatedAtAsc(any(), any());
    }

    @Test
    void matchForReturnsFirstMatchingRuleAndIncrementsCount() {
        UUID cat1 = UUID.randomUUID();
        UUID cat2 = UUID.randomUUID();
        TransactionCategoryRule r1 = rule("bim", cat1, TxnType.EXPENSE, 10);
        TransactionCategoryRule r2 = rule("market", cat2, TxnType.EXPENSE, 20);
        when(ruleRepo.findByUserIdAndTxnTypeOrderByPriorityAscCreatedAtAsc(userId, TxnType.EXPENSE))
                .thenReturn(List.of(r1, r2));

        Optional<UUID> res = service.matchFor(userId, TxnType.EXPENSE, "BIM Market alisveris");

        assertThat(res).contains(cat1);
        assertThat(r1.getMatchCount()).isEqualTo(1);
        verify(ruleRepo).save(r1);
    }

    @Test
    void matchForIsCaseInsensitive() {
        UUID catId = UUID.randomUUID();
        TransactionCategoryRule r = rule("BIM", catId, TxnType.EXPENSE, 100);
        when(ruleRepo.findByUserIdAndTxnTypeOrderByPriorityAscCreatedAtAsc(userId, TxnType.EXPENSE))
                .thenReturn(List.of(r));

        assertThat(service.matchFor(userId, TxnType.EXPENSE, "bim alisveris")).contains(catId);
    }

    @Test
    void matchForReturnsEmptyAndSavesNothingWhenNoMatch() {
        TransactionCategoryRule r = rule("bim", UUID.randomUUID(), TxnType.EXPENSE, 10);
        when(ruleRepo.findByUserIdAndTxnTypeOrderByPriorityAscCreatedAtAsc(userId, TxnType.EXPENSE))
                .thenReturn(List.of(r));

        assertThat(service.matchFor(userId, TxnType.EXPENSE, "random text")).isEmpty();
        verify(ruleRepo, never()).save(any());
    }

    @Test
    void matchForSkipsBlankPatterns() {
        TransactionCategoryRule blank = rule("   ", UUID.randomUUID(), TxnType.EXPENSE, 1);
        UUID cat = UUID.randomUUID();
        TransactionCategoryRule good = rule("abc", cat, TxnType.EXPENSE, 2);
        when(ruleRepo.findByUserIdAndTxnTypeOrderByPriorityAscCreatedAtAsc(userId, TxnType.EXPENSE))
                .thenReturn(List.of(blank, good));

        assertThat(service.matchFor(userId, TxnType.EXPENSE, "xyz abc def")).contains(cat);
    }
}
