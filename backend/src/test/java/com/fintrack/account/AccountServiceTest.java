package com.fintrack.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.account.dto.AccountResponse;
import com.fintrack.account.dto.AccountTotalsResponse;
import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.dto.UpdateAccountRequest;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.Account.AccountType;
import com.fintrack.common.exception.BusinessRuleException;
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
class AccountServiceTest {

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
    void listReturnsArchiveFilteredResponses() {
        when(accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(account("Main"), account("Crypto")));

        List<AccountResponse> res = service.listForUser(userId);

        assertThat(res).extracting(AccountResponse::name).containsExactly("Main", "Crypto");
    }

    @Test
    void getThrows404WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForUser(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createPersistsAndReturnsResponse() {
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

        AccountResponse res = service.create(userId, createRequest("Main"));

        assertThat(res.name()).isEqualTo("Main");
        assertThat(res.currency()).isEqualTo("TRY");
        assertThat(res.archived()).isFalse();
    }

    @Test
    void createTrimsNameAndUppercasesCurrency() {
        when(accountRepository.countByUserIdAndArchivedFalse(userId)).thenReturn(0L);
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(eq(userId), any()))
                .thenReturn(false);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(
                userId,
                new CreateAccountRequest(
                        "  Main  ",
                        AccountType.BANK_SAVINGS,
                        "usd",
                        "  Akbank  ",
                        "1234",
                        "  note  ",
                        null));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Main");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getInstitution()).isEqualTo("Akbank");
        assertThat(saved.getNotes()).isEqualTo("note");
    }

    @Test
    void createThrowsWhenAtCap() {
        when(accountRepository.countByUserIdAndArchivedFalse(userId)).thenReturn(50L);

        assertThatThrownBy(() -> service.create(userId, createRequest("Main")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Account limit reached")
                .extracting("code")
                .isEqualTo("ACCOUNT_LIMIT");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void createThrowsOnDuplicateNameCaseInsensitive() {
        when(accountRepository.countByUserIdAndArchivedFalse(userId)).thenReturn(0L);
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(userId, "Main"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, createRequest("Main")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_NAME_DUPLICATE");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void createTreatsNullOpeningBalanceAsZero() {
        when(accountRepository.countByUserIdAndArchivedFalse(userId)).thenReturn(0L);
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndArchivedFalse(eq(userId), any()))
                .thenReturn(false);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, createRequest("Main"));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualByComparingTo("0");
    }

    @Test
    void updateThrows404WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId,
                                        id,
                                        new UpdateAccountRequest(
                                                "New",
                                                "TRY",
                                                null,
                                                null,
                                                null,
                                                BigDecimal.ZERO)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatePersistsMutatedFields() {
        UUID id = UUID.randomUUID();
        Account existing = account("Old");
        existing.setId(id);
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.of(existing));

        service.update(
                userId,
                id,
                new UpdateAccountRequest(
                        "  New  ",
                        "usd",
                        "  Garanti  ",
                        "9876",
                        "  note  ",
                        new BigDecimal("123.45")));

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getCurrency()).isEqualTo("USD");
        assertThat(existing.getInstitution()).isEqualTo("Garanti");
        assertThat(existing.getAccountNumberSuffix()).isEqualTo("9876");
        assertThat(existing.getNotes()).isEqualTo("note");
        assertThat(existing.getCurrentBalance()).isEqualByComparingTo("123.45");
        assertThat(existing.getAccountType()).isEqualTo(AccountType.BANK_CHECKING);
    }

    @Test
    void updateThrowsWhenRenamingToExistingName() {
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
                                                "Taken",
                                                "TRY",
                                                null,
                                                null,
                                                null,
                                                BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_NAME_DUPLICATE");
    }

    @Test
    void updateSkipsDuplicateGuardWhenNameUnchanged() {
        UUID id = UUID.randomUUID();
        Account existing = account("Main");
        existing.setId(id);
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.of(existing));

        service.update(
                userId,
                id,
                new UpdateAccountRequest(
                        "main", "TRY", null, null, null, new BigDecimal("10")));

        verify(accountRepository, never())
                .existsByUserIdAndNameIgnoreCaseAndArchivedFalse(any(), any());
        assertThat(existing.getName()).isEqualTo("main");
        assertThat(existing.getCurrentBalance()).isEqualByComparingTo("10");
    }

    @Test
    void deleteSetsArchivedTrue() {
        UUID id = UUID.randomUUID();
        Account existing = account("Main");
        existing.setId(id);
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.of(existing));

        service.delete(userId, id);

        assertThat(existing.isArchived()).isTrue();
    }

    @Test
    void deleteThrows404WhenAlreadyArchived() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(id, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void totalsReturnsLiveAndArchivedCountsAndCurrencyRollup() {
        when(accountRepository.countByUserIdAndArchivedFalse(userId)).thenReturn(3L);
        when(accountRepository.countByUserIdAndArchivedTrue(userId)).thenReturn(1L);
        when(accountRepository.sumBalancesByCurrencyForUser(userId))
                .thenReturn(
                        List.of(
                                new Object[] {"TRY", new BigDecimal("1000")},
                                new Object[] {"USD", new BigDecimal("250")}));

        AccountTotalsResponse totals = service.totalsForUser(userId);

        assertThat(totals.liveCount()).isEqualTo(3);
        assertThat(totals.archivedCount()).isEqualTo(1);
        assertThat(totals.byCurrency()).hasSize(2);
        assertThat(totals.byCurrency().get(0).currency()).isEqualTo("TRY");
        assertThat(totals.byCurrency().get(0).totalBalance()).isEqualByComparingTo("1000");
        assertThat(totals.byCurrency().get(1).currency()).isEqualTo("USD");
        assertThat(totals.byCurrency().get(1).totalBalance()).isEqualByComparingTo("250");
    }
}
