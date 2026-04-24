package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.budget.dto.CategoriesResponse;
import com.fintrack.budget.dto.CategoryResponse;
import com.fintrack.budget.dto.CreateCategoryRequest;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
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
class CategoryServiceTest {

    @Mock IncomeCategoryRepository incomeRepo;
    @Mock ExpenseCategoryRepository expenseRepo;

    @InjectMocks CategoryService service;

    private final UUID userId = UUID.randomUUID();

    private IncomeCategory income(String name) {
        return IncomeCategory.builder().id(UUID.randomUUID()).userId(userId).name(name).build();
    }

    private ExpenseCategory expense(String name, String budget) {
        return ExpenseCategory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .budgetAmount(budget == null ? null : new BigDecimal(budget))
                .rolloverEnabled(false)
                .build();
    }

    @Test
    void listAllPartitionsIncomeAndExpense() {
        when(incomeRepo.findByUserIdOrderByNameAsc(userId))
                .thenReturn(List.of(income("Salary"), income("Bonus")));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId))
                .thenReturn(List.of(expense("Food", "500"), expense("Rent", "3000")));

        CategoriesResponse res = service.listAll(userId);

        assertThat(res.income())
                .extracting(CategoryResponse::name)
                .containsExactly("Salary", "Bonus");
        assertThat(res.expense())
                .extracting(CategoryResponse::name)
                .containsExactly("Food", "Rent");
        assertThat(res.expense().get(0).budgetAmount()).isEqualByComparingTo("500");
    }

    @Test
    void createIncomePersistsWithoutBudgetFields() {
        when(incomeRepo.save(any(IncomeCategory.class)))
                .thenAnswer(
                        inv -> {
                            IncomeCategory c = inv.getArgument(0);
                            c.setId(UUID.randomUUID());
                            return c;
                        });

        CategoryResponse res =
                service.createIncome(
                        userId, new CreateCategoryRequest("Salary", "money", "#0f0", null, null));

        ArgumentCaptor<IncomeCategory> captor = ArgumentCaptor.forClass(IncomeCategory.class);
        verify(incomeRepo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getName()).isEqualTo("Salary");
        assertThat(captor.getValue().getIcon()).isEqualTo("money");
        assertThat(captor.getValue().getColor()).isEqualTo("#0f0");
        assertThat(res.budgetAmount()).isNull();
        assertThat(res.rolloverEnabled()).isFalse();
    }

    @Test
    void createExpensePersistsWithBudgetAndRolloverFlag() {
        when(expenseRepo.save(any(ExpenseCategory.class)))
                .thenAnswer(
                        inv -> {
                            ExpenseCategory c = inv.getArgument(0);
                            c.setId(UUID.randomUUID());
                            return c;
                        });

        CategoryResponse res =
                service.createExpense(
                        userId,
                        new CreateCategoryRequest(
                                "Food", "fork", "#f00", new BigDecimal("500"), Boolean.TRUE));

        ArgumentCaptor<ExpenseCategory> captor = ArgumentCaptor.forClass(ExpenseCategory.class);
        verify(expenseRepo).save(captor.capture());
        ExpenseCategory saved = captor.getValue();
        assertThat(saved.getBudgetAmount()).isEqualByComparingTo("500");
        assertThat(saved.isRolloverEnabled()).isTrue();
        assertThat(res.rolloverEnabled()).isTrue();
    }

    @Test
    void createExpenseTreatsRolloverEnabledNullAsFalse() {
        when(expenseRepo.save(any(ExpenseCategory.class)))
                .thenAnswer(
                        inv -> {
                            ExpenseCategory c = inv.getArgument(0);
                            c.setId(UUID.randomUUID());
                            return c;
                        });

        service.createExpense(userId, new CreateCategoryRequest("Food", null, null, null, null));

        ArgumentCaptor<ExpenseCategory> captor = ArgumentCaptor.forClass(ExpenseCategory.class);
        verify(expenseRepo).save(captor.capture());
        assertThat(captor.getValue().isRolloverEnabled()).isFalse();
    }

    @Test
    void updateIncomeRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(incomeRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.updateIncome(
                                        userId,
                                        id,
                                        new CreateCategoryRequest("x", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateIncomeMutatesFields() {
        IncomeCategory existing = income("Old");
        when(incomeRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.updateIncome(
                userId,
                existing.getId(),
                new CreateCategoryRequest("New", "star", "#abc", null, null));

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getIcon()).isEqualTo("star");
        assertThat(existing.getColor()).isEqualTo("#abc");
    }

    @Test
    void updateExpenseRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(expenseRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.updateExpense(
                                        userId,
                                        id,
                                        new CreateCategoryRequest("x", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateExpenseMutatesAllFieldsIncludingBudgetAndRollover() {
        ExpenseCategory existing = expense("Old", "100");
        existing.setRolloverEnabled(false);
        when(expenseRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.updateExpense(
                userId,
                existing.getId(),
                new CreateCategoryRequest(
                        "New", "icon", "#333", new BigDecimal("750"), Boolean.TRUE));

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getBudgetAmount()).isEqualByComparingTo("750");
        assertThat(existing.isRolloverEnabled()).isTrue();
    }

    @Test
    void updateExpenseRolloverFlagNullTurnsItOff() {
        ExpenseCategory existing = expense("Old", "100");
        existing.setRolloverEnabled(true);
        when(expenseRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.updateExpense(
                userId, existing.getId(), new CreateCategoryRequest("Old", null, null, null, null));

        assertThat(existing.isRolloverEnabled()).isFalse();
    }

    @Test
    void deleteIncomeRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(incomeRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteIncome(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(incomeRepo, never()).delete(any());
    }

    @Test
    void deleteIncomeRemovesWhenOwned() {
        IncomeCategory existing = income("Salary");
        when(incomeRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.deleteIncome(userId, existing.getId());

        verify(incomeRepo).delete(existing);
    }

    @Test
    void deleteExpenseRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(expenseRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteExpense(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(expenseRepo, never()).delete(any());
    }

    @Test
    void deleteExpenseRemovesWhenOwned() {
        ExpenseCategory existing = expense("Food", "500");
        when(expenseRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.deleteExpense(userId, existing.getId());

        verify(expenseRepo).delete(existing);
    }
}
