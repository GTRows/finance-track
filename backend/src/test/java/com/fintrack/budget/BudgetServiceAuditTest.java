package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.budget.dto.CreateTransactionRequest;
import com.fintrack.budget.dto.UpdateTransactionRequest;
import com.fintrack.budget.rule.TransactionCategoryRuleService;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.price.FxConversionService;
import com.fintrack.settings.UserSettingsRepository;
import com.fintrack.tag.TagService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BudgetServiceAuditTest {

    @Mock TransactionRepository txnRepo;
    @Mock IncomeCategoryRepository incomeRepo;
    @Mock ExpenseCategoryRepository expenseRepo;
    @Mock MonthlySummaryRepository summaryRepo;
    @Mock TransactionCategoryRuleService categoryRuleService;
    @Mock TagService tagService;
    @Mock UserSettingsRepository userSettingsRepo;
    @Mock FxConversionService fxConversionService;
    @Mock AuditService auditService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks BudgetService service;

    private final UUID userId = UUID.randomUUID();

    private CreateTransactionRequest createReq() {
        return new CreateTransactionRequest(
                TxnType.EXPENSE,
                new BigDecimal("100"),
                "TRY",
                null,
                "lunch",
                LocalDate.of(2026, 4, 10),
                false,
                List.of());
    }

    private UpdateTransactionRequest updateReq() {
        return new UpdateTransactionRequest(
                TxnType.EXPENSE,
                new BigDecimal("125"),
                "TRY",
                null,
                "dinner",
                LocalDate.of(2026, 4, 11),
                false,
                List.of());
    }

    private BudgetTransaction txn(UUID id) {
        return BudgetTransaction.builder()
                .id(id)
                .userId(userId)
                .txnType(TxnType.EXPENSE)
                .amount(new BigDecimal("100"))
                .currency("TRY")
                .txnDate(LocalDate.of(2026, 4, 10))
                .build();
    }

    @Test
    void createEmitsCreatedAudit() {
        when(txnRepo.save(any()))
                .thenAnswer(
                        inv -> {
                            BudgetTransaction t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });
        when(tagService.resolveOwnedIds(eq(userId), any())).thenReturn(List.of());
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(tagService.loadTagsForTransactions(eq(userId), any())).thenReturn(java.util.Map.of());
        when(categoryRuleService.matchFor(eq(userId), eq(TxnType.EXPENSE), any()))
                .thenReturn(Optional.empty());

        service.create(userId, createReq());

        verify(auditService)
                .success(
                        eq(AuditAction.BUDGET_TRANSACTION_CREATED),
                        eq(userId),
                        any(),
                        contains("id="));
    }

    @Test
    void updateEmitsUpdatedAuditWithTxnId() {
        UUID id = UUID.randomUUID();
        when(txnRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(txn(id)));
        when(tagService.resolveOwnedIds(eq(userId), any())).thenReturn(List.of());
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(tagService.loadTagsForTransactions(eq(userId), any())).thenReturn(java.util.Map.of());

        service.update(userId, id, updateReq());

        verify(auditService)
                .success(
                        eq(AuditAction.BUDGET_TRANSACTION_UPDATED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }

    @Test
    void deleteEmitsDeletedAudit() {
        UUID id = UUID.randomUUID();
        when(txnRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(txn(id)));

        service.delete(userId, id);

        verify(auditService)
                .success(
                        eq(AuditAction.BUDGET_TRANSACTION_DELETED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }

    @Test
    void bulkDeleteEmitsAuditOnlyWhenAtLeastOneRowMatches() {
        UUID id = UUID.randomUUID();
        when(txnRepo.findByIdInAndUserId(List.of(id), userId)).thenReturn(List.of(txn(id)));

        int affected = service.bulkDelete(userId, List.of(id));

        assertThat(affected).isEqualTo(1);
        verify(auditService)
                .success(
                        eq(AuditAction.BUDGET_TRANSACTION_DELETED),
                        eq(userId),
                        any(),
                        contains("count=1"));
    }

    @Test
    void bulkDeleteEmitsNoAuditWhenNothingMatches() {
        UUID id = UUID.randomUUID();
        when(txnRepo.findByIdInAndUserId(List.of(id), userId)).thenReturn(List.of());

        int affected = service.bulkDelete(userId, List.of(id));

        assertThat(affected).isZero();
        verify(auditService, never()).success(any(), any(), any(), any());
    }

    @Test
    void bulkDeleteEmitsNoAuditWhenIdsAreNullOrEmpty() {
        assertThat(service.bulkDelete(userId, null)).isZero();
        assertThat(service.bulkDelete(userId, List.of())).isZero();
        verify(auditService, never()).success(any(), any(), any(), any());
    }
}
