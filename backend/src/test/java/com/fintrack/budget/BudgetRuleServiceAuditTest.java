package com.fintrack.budget;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.alert.AlertNotificationRepository;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.budget.dto.CreateBudgetRuleRequest;
import com.fintrack.common.entity.BudgetRule;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.metrics.BusinessMetrics;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetRuleServiceAuditTest {

    @Mock BudgetRuleRepository ruleRepo;
    @Mock ExpenseCategoryRepository expenseRepo;
    @Mock TransactionRepository txnRepo;
    @Mock AlertNotificationRepository notificationRepo;
    @Mock BusinessMetrics businessMetrics;
    @Mock AuditService auditService;

    @InjectMocks BudgetRuleService service;

    private final UUID userId = UUID.randomUUID();

    private ExpenseCategory cat() {
        return ExpenseCategory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Food")
                .color("#f00")
                .build();
    }

    @Test
    void createInsertsNewRuleEmitsCreatedAudit() {
        ExpenseCategory food = cat();
        when(expenseRepo.findByIdAndUserId(food.getId(), userId)).thenReturn(Optional.of(food));
        when(ruleRepo.findByUserIdAndCategoryId(userId, food.getId())).thenReturn(Optional.empty());
        when(ruleRepo.save(any(BudgetRule.class)))
                .thenAnswer(
                        inv -> {
                            BudgetRule r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            return r;
                        });

        service.create(userId, new CreateBudgetRuleRequest(food.getId(), new BigDecimal("500")));

        verify(auditService)
                .success(eq(AuditAction.BUDGET_RULE_CREATED), eq(userId), any(), contains("id="));
    }

    @Test
    void createUpdatesExistingRuleEmitsUpdatedAudit() {
        ExpenseCategory food = cat();
        BudgetRule existing =
                BudgetRule.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .categoryId(food.getId())
                        .monthlyLimitTry(new BigDecimal("100"))
                        .active(true)
                        .build();
        when(expenseRepo.findByIdAndUserId(food.getId(), userId)).thenReturn(Optional.of(food));
        when(ruleRepo.findByUserIdAndCategoryId(userId, food.getId()))
                .thenReturn(Optional.of(existing));
        when(ruleRepo.save(existing)).thenReturn(existing);

        service.create(userId, new CreateBudgetRuleRequest(food.getId(), new BigDecimal("800")));

        verify(auditService)
                .success(
                        eq(AuditAction.BUDGET_RULE_UPDATED),
                        eq(userId),
                        any(),
                        contains(existing.getId().toString()));
    }

    @Test
    void deleteEmitsDeletedAudit() {
        BudgetRule rule =
                BudgetRule.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .categoryId(UUID.randomUUID())
                        .monthlyLimitTry(BigDecimal.ONE)
                        .active(true)
                        .build();
        when(ruleRepo.findByIdAndUserId(rule.getId(), userId)).thenReturn(Optional.of(rule));

        service.delete(userId, rule.getId());

        verify(auditService)
                .success(
                        eq(AuditAction.BUDGET_RULE_DELETED),
                        eq(userId),
                        any(),
                        contains(rule.getId().toString()));
    }
}
