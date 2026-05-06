package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditService;
import com.fintrack.budget.dto.CreateCategoryRequest;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CategoryServiceCacheTest.TestConfig.class)
class CategoryServiceCacheTest {

    @EnableCaching
    @Import({CacheConfig.class, CategoryService.class})
    static class TestConfig {}

    @MockBean IncomeCategoryRepository incomeRepo;
    @MockBean ExpenseCategoryRepository expenseRepo;
    @MockBean AuditService auditService;

    @Autowired CategoryService service;
    @Autowired CacheManager cacheManager;

    private final UUID userId = UUID.randomUUID();

    private IncomeCategory income(String name) {
        return IncomeCategory.builder().id(UUID.randomUUID()).userId(userId).name(name).build();
    }

    private ExpenseCategory expense(String name) {
        return ExpenseCategory.builder().id(UUID.randomUUID()).userId(userId).name(name).build();
    }

    @BeforeEach
    void clearCaches() {
        cacheManager.getCache(CacheConfig.CATEGORY_LOOKUP_CACHE).clear();
    }

    @Test
    void listAllSecondCallServedFromCache() {
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(income("Salary")));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(expense("Food")));

        service.listAll(userId);
        service.listAll(userId);

        verify(incomeRepo, times(1)).findByUserIdOrderByNameAsc(userId);
        verify(expenseRepo, times(1)).findByUserIdOrderByNameAsc(userId);
    }

    @Test
    void differentUsersHaveSeparateCacheEntries() {
        UUID other = UUID.randomUUID();
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(incomeRepo.findByUserIdOrderByNameAsc(other)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(other)).thenReturn(List.of());

        service.listAll(userId);
        service.listAll(other);
        service.listAll(userId);
        service.listAll(other);

        verify(incomeRepo, times(1)).findByUserIdOrderByNameAsc(userId);
        verify(incomeRepo, times(1)).findByUserIdOrderByNameAsc(other);
    }

    @Test
    void createIncomeEvictsCacheForUser() {
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(incomeRepo.save(any(IncomeCategory.class)))
                .thenAnswer(
                        inv -> {
                            IncomeCategory c = inv.getArgument(0);
                            c.setId(UUID.randomUUID());
                            return c;
                        });

        service.listAll(userId);
        service.createIncome(userId, new CreateCategoryRequest("Salary", null, null, null, null));
        service.listAll(userId);

        verify(incomeRepo, times(2)).findByUserIdOrderByNameAsc(userId);
    }

    @Test
    void createExpenseEvictsCacheForUser() {
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.save(any(ExpenseCategory.class)))
                .thenAnswer(
                        inv -> {
                            ExpenseCategory c = inv.getArgument(0);
                            c.setId(UUID.randomUUID());
                            return c;
                        });

        service.listAll(userId);
        service.createExpense(userId, new CreateCategoryRequest("Food", null, null, null, null));
        service.listAll(userId);

        verify(expenseRepo, times(2)).findByUserIdOrderByNameAsc(userId);
    }

    @Test
    void updateIncomeEvictsCacheForUser() {
        IncomeCategory existing = income("Old");
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(incomeRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.listAll(userId);
        service.updateIncome(
                userId, existing.getId(), new CreateCategoryRequest("New", null, null, null, null));
        service.listAll(userId);

        verify(incomeRepo, times(2)).findByUserIdOrderByNameAsc(userId);
    }

    @Test
    void updateExpenseEvictsCacheForUser() {
        ExpenseCategory existing = expense("Old");
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.listAll(userId);
        service.updateExpense(
                userId, existing.getId(), new CreateCategoryRequest("New", null, null, null, null));
        service.listAll(userId);

        verify(expenseRepo, times(2)).findByUserIdOrderByNameAsc(userId);
    }

    @Test
    void deleteIncomeEvictsCacheForUser() {
        IncomeCategory existing = income("Salary");
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(incomeRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.listAll(userId);
        service.deleteIncome(userId, existing.getId());
        service.listAll(userId);

        verify(incomeRepo, times(2)).findByUserIdOrderByNameAsc(userId);
    }

    @Test
    void deleteExpenseEvictsCacheForUser() {
        ExpenseCategory existing = expense("Food");
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.listAll(userId);
        service.deleteExpense(userId, existing.getId());
        service.listAll(userId);

        verify(expenseRepo, times(2)).findByUserIdOrderByNameAsc(userId);
    }

    @Test
    void failedUpdateDoesNotEvictCache() {
        UUID badId = UUID.randomUUID();
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(incomeRepo.findByIdAndUserId(badId, userId)).thenReturn(Optional.empty());

        service.listAll(userId);
        assertThatThrownBy(
                        () ->
                                service.updateIncome(
                                        userId,
                                        badId,
                                        new CreateCategoryRequest("X", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        service.listAll(userId);

        // Cache held the entry through the failed update; only one repo read happened.
        verify(incomeRepo, times(1)).findByUserIdOrderByNameAsc(userId);
    }
}
