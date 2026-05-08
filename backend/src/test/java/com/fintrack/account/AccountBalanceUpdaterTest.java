package com.fintrack.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.Account;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountBalanceUpdaterTest {

    @Mock AccountRepository accountRepository;

    @InjectMocks AccountBalanceUpdater updater;

    private final UUID accountId = UUID.randomUUID();

    @Test
    void apply_addsDeltaWhenAccountExists() {
        Account account =
                Account.builder()
                        .id(accountId)
                        .userId(UUID.randomUUID())
                        .name("Garanti")
                        .accountType(Account.AccountType.BANK_CHECKING)
                        .currency("TRY")
                        .currentBalance(new BigDecimal("1000"))
                        .build();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        updater.apply(accountId, new BigDecimal("250"));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getCurrentBalance()).isEqualByComparingTo("1250");
    }

    @Test
    void apply_skipsWhenAccountNotFound() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        updater.apply(accountId, new BigDecimal("100"));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void apply_skipsOnNullAccountId() {
        updater.apply(null, new BigDecimal("100"));
        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void apply_skipsOnNullDelta() {
        updater.apply(accountId, null);
        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void apply_skipsOnZeroDelta() {
        updater.apply(accountId, BigDecimal.ZERO);
        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void apply_treatsNullCurrentBalanceAsZero() {
        Account account =
                Account.builder()
                        .id(accountId)
                        .userId(UUID.randomUUID())
                        .name("Garanti")
                        .accountType(Account.AccountType.BANK_CHECKING)
                        .currency("TRY")
                        .currentBalance(null)
                        .build();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        updater.apply(accountId, new BigDecimal("75"));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getCurrentBalance()).isEqualByComparingTo("75");
    }
}
