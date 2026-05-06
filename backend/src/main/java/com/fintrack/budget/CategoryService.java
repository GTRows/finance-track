package com.fintrack.budget;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.budget.dto.CategoriesResponse;
import com.fintrack.budget.dto.CategoryResponse;
import com.fintrack.budget.dto.CreateCategoryRequest;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final IncomeCategoryRepository incomeRepo;
    private final ExpenseCategoryRepository expenseRepo;
    private final AuditService auditService;

    @Cacheable(value = CacheConfig.CATEGORY_LOOKUP_CACHE, key = "#userId")
    @Transactional(readOnly = true)
    public CategoriesResponse listAll(UUID userId) {
        var income =
                incomeRepo.findByUserIdOrderByNameAsc(userId).stream()
                        .map(CategoryResponse::from)
                        .toList();
        var expense =
                expenseRepo.findByUserIdOrderByNameAsc(userId).stream()
                        .map(CategoryResponse::from)
                        .toList();
        return new CategoriesResponse(income, expense);
    }

    @CacheEvict(value = CacheConfig.CATEGORY_LOOKUP_CACHE, key = "#userId")
    @Transactional
    public CategoryResponse createIncome(UUID userId, CreateCategoryRequest req) {
        IncomeCategory cat =
                IncomeCategory.builder()
                        .userId(userId)
                        .name(req.name())
                        .icon(req.icon())
                        .color(req.color())
                        .build();
        IncomeCategory saved = incomeRepo.save(cat);
        auditService.success(
                AuditAction.CATEGORY_CREATED,
                userId,
                currentUsername(),
                "income id=" + saved.getId());
        return CategoryResponse.from(saved);
    }

    @CacheEvict(value = CacheConfig.CATEGORY_LOOKUP_CACHE, key = "#userId")
    @Transactional
    public CategoryResponse createExpense(UUID userId, CreateCategoryRequest req) {
        ExpenseCategory cat =
                ExpenseCategory.builder()
                        .userId(userId)
                        .name(req.name())
                        .icon(req.icon())
                        .color(req.color())
                        .budgetAmount(req.budgetAmount())
                        .rolloverEnabled(Boolean.TRUE.equals(req.rolloverEnabled()))
                        .build();
        ExpenseCategory saved = expenseRepo.save(cat);
        auditService.success(
                AuditAction.CATEGORY_CREATED,
                userId,
                currentUsername(),
                "expense id=" + saved.getId());
        return CategoryResponse.from(saved);
    }

    @CacheEvict(value = CacheConfig.CATEGORY_LOOKUP_CACHE, key = "#userId")
    @Transactional
    public CategoryResponse updateIncome(UUID userId, UUID id, CreateCategoryRequest req) {
        IncomeCategory cat =
                incomeRepo
                        .findByIdAndUserId(id, userId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Income category not found"));
        cat.setName(req.name());
        cat.setIcon(req.icon());
        cat.setColor(req.color());
        auditService.success(
                AuditAction.CATEGORY_UPDATED, userId, currentUsername(), "income id=" + id);
        return CategoryResponse.from(cat);
    }

    @CacheEvict(value = CacheConfig.CATEGORY_LOOKUP_CACHE, key = "#userId")
    @Transactional
    public CategoryResponse updateExpense(UUID userId, UUID id, CreateCategoryRequest req) {
        ExpenseCategory cat =
                expenseRepo
                        .findByIdAndUserId(id, userId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Expense category not found"));
        cat.setName(req.name());
        cat.setIcon(req.icon());
        cat.setColor(req.color());
        cat.setBudgetAmount(req.budgetAmount());
        cat.setRolloverEnabled(Boolean.TRUE.equals(req.rolloverEnabled()));
        auditService.success(
                AuditAction.CATEGORY_UPDATED, userId, currentUsername(), "expense id=" + id);
        return CategoryResponse.from(cat);
    }

    @CacheEvict(value = CacheConfig.CATEGORY_LOOKUP_CACHE, key = "#userId")
    @Transactional
    public void deleteIncome(UUID userId, UUID id) {
        IncomeCategory cat =
                incomeRepo
                        .findByIdAndUserId(id, userId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Income category not found"));
        incomeRepo.delete(cat);
        auditService.success(
                AuditAction.CATEGORY_DELETED, userId, currentUsername(), "income id=" + id);
    }

    @CacheEvict(value = CacheConfig.CATEGORY_LOOKUP_CACHE, key = "#userId")
    @Transactional
    public void deleteExpense(UUID userId, UUID id) {
        ExpenseCategory cat =
                expenseRepo
                        .findByIdAndUserId(id, userId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Expense category not found"));
        expenseRepo.delete(cat);
        auditService.success(
                AuditAction.CATEGORY_DELETED, userId, currentUsername(), "expense id=" + id);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
