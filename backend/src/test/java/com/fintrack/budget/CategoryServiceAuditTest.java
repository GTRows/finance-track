package com.fintrack.budget;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.budget.dto.CreateCategoryRequest;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceAuditTest {

    @Mock IncomeCategoryRepository incomeRepo;
    @Mock ExpenseCategoryRepository expenseRepo;
    @Mock AuditService auditService;

    @InjectMocks CategoryService service;

    private final UUID userId = UUID.randomUUID();

    private CreateCategoryRequest req(String name, String budget) {
        return new CreateCategoryRequest(
                name, "icon", "#abc", budget == null ? null : new BigDecimal(budget), null);
    }

    @Test
    void createIncomeEmitsCreatedAudit() {
        when(incomeRepo.save(any(IncomeCategory.class)))
                .thenAnswer(
                        inv -> {
                            IncomeCategory c = inv.getArgument(0);
                            c.setId(UUID.randomUUID());
                            return c;
                        });

        service.createIncome(userId, req("Salary", null));

        verify(auditService)
                .success(eq(AuditAction.CATEGORY_CREATED), eq(userId), any(), contains("income"));
    }

    @Test
    void createExpenseEmitsCreatedAudit() {
        when(expenseRepo.save(any(ExpenseCategory.class)))
                .thenAnswer(
                        inv -> {
                            ExpenseCategory c = inv.getArgument(0);
                            c.setId(UUID.randomUUID());
                            return c;
                        });

        service.createExpense(userId, req("Food", "500"));

        verify(auditService)
                .success(eq(AuditAction.CATEGORY_CREATED), eq(userId), any(), contains("expense"));
    }

    @Test
    void updateIncomeEmitsUpdatedAudit() {
        UUID id = UUID.randomUUID();
        IncomeCategory cat = IncomeCategory.builder().id(id).userId(userId).name("Old").build();
        when(incomeRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(cat));

        service.updateIncome(userId, id, req("New", null));

        verify(auditService)
                .success(
                        eq(AuditAction.CATEGORY_UPDATED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }

    @Test
    void updateExpenseEmitsUpdatedAudit() {
        UUID id = UUID.randomUUID();
        ExpenseCategory cat =
                ExpenseCategory.builder()
                        .id(id)
                        .userId(userId)
                        .name("Old")
                        .budgetAmount(BigDecimal.TEN)
                        .build();
        when(expenseRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(cat));

        service.updateExpense(userId, id, req("New", "200"));

        verify(auditService)
                .success(
                        eq(AuditAction.CATEGORY_UPDATED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }

    @Test
    void deleteIncomeEmitsDeletedAudit() {
        UUID id = UUID.randomUUID();
        IncomeCategory cat = IncomeCategory.builder().id(id).userId(userId).name("Salary").build();
        when(incomeRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(cat));

        service.deleteIncome(userId, id);

        verify(auditService)
                .success(
                        eq(AuditAction.CATEGORY_DELETED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }

    @Test
    void deleteExpenseEmitsDeletedAudit() {
        UUID id = UUID.randomUUID();
        ExpenseCategory cat =
                ExpenseCategory.builder()
                        .id(id)
                        .userId(userId)
                        .name("Food")
                        .budgetAmount(BigDecimal.TEN)
                        .build();
        when(expenseRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(cat));

        service.deleteExpense(userId, id);

        verify(auditService)
                .success(
                        eq(AuditAction.CATEGORY_DELETED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }
}
