package com.fintrack.budget;

import com.fintrack.budget.dto.CategoriesResponse;
import com.fintrack.budget.dto.CategoryResponse;
import com.fintrack.budget.dto.CreateCategoryRequest;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final IncomeCategoryRepository incomeRepo;
    private final ExpenseCategoryRepository expenseRepo;

    @Transactional(readOnly = true)
    public CategoriesResponse listAll(UUID userId) {
        var income = incomeRepo.findByUserIdOrderByNameAsc(userId).stream()
                .map(CategoryResponse::from).toList();
        var expense = expenseRepo.findByUserIdOrderByNameAsc(userId).stream()
                .map(CategoryResponse::from).toList();
        return new CategoriesResponse(income, expense);
    }

    @Transactional
    public CategoryResponse createIncome(UUID userId, CreateCategoryRequest req) {
        IncomeCategory cat = IncomeCategory.builder()
                .userId(userId)
                .name(req.name())
                .icon(req.icon())
                .color(req.color())
                .build();
        return CategoryResponse.from(incomeRepo.save(cat));
    }

    @Transactional
    public CategoryResponse createExpense(UUID userId, CreateCategoryRequest req) {
        ExpenseCategory cat = ExpenseCategory.builder()
                .userId(userId)
                .name(req.name())
                .icon(req.icon())
                .color(req.color())
                .budgetAmount(req.budgetAmount())
                .build();
        return CategoryResponse.from(expenseRepo.save(cat));
    }

    @Transactional
    public CategoryResponse updateIncome(UUID userId, UUID id, CreateCategoryRequest req) {
        IncomeCategory cat = incomeRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Income category not found"));
        cat.setName(req.name());
        cat.setIcon(req.icon());
        cat.setColor(req.color());
        return CategoryResponse.from(cat);
    }

    @Transactional
    public CategoryResponse updateExpense(UUID userId, UUID id, CreateCategoryRequest req) {
        ExpenseCategory cat = expenseRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found"));
        cat.setName(req.name());
        cat.setIcon(req.icon());
        cat.setColor(req.color());
        cat.setBudgetAmount(req.budgetAmount());
        return CategoryResponse.from(cat);
    }

    @Transactional
    public void deleteIncome(UUID userId, UUID id) {
        IncomeCategory cat = incomeRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Income category not found"));
        incomeRepo.delete(cat);
    }

    @Transactional
    public void deleteExpense(UUID userId, UUID id) {
        ExpenseCategory cat = expenseRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found"));
        expenseRepo.delete(cat);
    }
}
