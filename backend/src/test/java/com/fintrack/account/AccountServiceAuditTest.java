package com.fintrack.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.dto.UpdateAccountRequest;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.Account.AccountType;
import com.fintrack.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceAuditTest {

    @Mock AccountRepository accountRepository;
    @Mock AuditService auditService;

    @InjectMocks AccountService service;

    private final UUID userId = UUID.randomUUID();

    private Account account(String name) {
        return Account.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .accountType(AccountType.BANK_CHECKING)
                .currency("TRY")
                .currentBalance(BigDecimal.ZERO)
                .archived(false)
                .build();
    }

    private CreateAccountRequest createRequest(String name) {
        return new CreateAccountRequest(
                name, AccountType.BANK_CHECKING, "TRY", null, null, null, null);
    }

    @Test
    void createEmitsSuccessAuditAfterPersist() {
        when(accountRepository.countByUserIdAndArchivedFalse(userId)).thenReturn(0L);
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(eq(userId), any()))
                .thenReturn(false);
        when(accountRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Account a = inv.getArgument(0);
                            a.setId(UUID.randomUUID());
                            return a;
                        });

        service.create(userId, createRequest("Main"));

        verify(auditService)
                .success(eq(AuditAction.ACCOUNT_CREATED), eq(userId), any(), contains("id="));
    }

    @Test
    void createEmitsFailureAuditWhenAtCap() {
        when(accountRepository.countByUserIdAndArchivedFalse(userId)).thenReturn(50L);

        assertThatThrownBy(() -> service.create(userId, createRequest("Main")))
                .isInstanceOf(BusinessRuleException.class);

        verify(auditService)
                .failure(
                        eq(AuditAction.ACCOUNT_CREATED),
                        eq(userId),
                        any(),
                        contains("Account limit"));
        verify(auditService, never()).success(any(), any(), any(), any());
    }

    @Test
    void createEmitsFailureAuditOnDuplicateName() {
        when(accountRepository.countByUserIdAndArchivedFalse(userId)).thenReturn(0L);
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(userId, "Main"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, createRequest("Main")))
                .isInstanceOf(BusinessRuleException.class);

        verify(auditService)
                .failure(
                        eq(AuditAction.ACCOUNT_CREATED),
                        eq(userId),
                        any(),
                        contains("already exists"));
        verify(auditService, never()).success(any(), any(), any(), any());
    }

    @Test
    void updateEmitsSuccessAuditAfterPersist() {
        UUID id = UUID.randomUUID();
        Account existing = account("Old");
        existing.setId(id);
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.of(existing));

        service.update(
                userId,
                id,
                new UpdateAccountRequest("Old", "TRY", null, null, null, BigDecimal.TEN));

        verify(auditService)
                .success(
                        eq(AuditAction.ACCOUNT_UPDATED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }

    @Test
    void updateEmitsFailureAuditOnDuplicateName() {
        UUID id = UUID.randomUUID();
        Account existing = account("Old");
        existing.setId(id);
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.of(existing));
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(userId, "Taken"))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId,
                                        id,
                                        new UpdateAccountRequest(
                                                "Taken", "TRY", null, null, null, BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class);

        verify(auditService)
                .failure(
                        eq(AuditAction.ACCOUNT_UPDATED),
                        eq(userId),
                        any(),
                        contains("already exists"));
    }

    @Test
    void deleteEmitsSuccessAuditAfterArchive() {
        UUID id = UUID.randomUUID();
        Account existing = account("Main");
        existing.setId(id);
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.of(existing));

        service.delete(userId, id);

        verify(auditService)
                .success(
                        eq(AuditAction.ACCOUNT_DELETED),
                        eq(userId),
                        any(),
                        contains(id.toString()));
    }
}
