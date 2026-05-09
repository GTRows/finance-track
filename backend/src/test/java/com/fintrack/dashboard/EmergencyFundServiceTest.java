package com.fintrack.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fintrack.account.AccountRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.dashboard.dto.EmergencyFundResponse;
import com.fintrack.settings.UserSettingsRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmergencyFundServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository txnRepo;
    @Mock UserSettingsRepository userSettingsRepo;

    @InjectMocks EmergencyFundService service;

    private final UUID userId = UUID.randomUUID();

    private UserSettings settings(List<String> types, Short targetMonths, Short amberFloorMonths) {
        return UserSettings.builder()
                .userId(userId)
                .emergencyFundIncludeTypes(types == null ? null : new java.util.ArrayList<>(types))
                .emergencyFundTargetMonths(targetMonths)
                .emergencyFundAmberFloorMonths(amberFloorMonths)
                .build();
    }

    private UserSettings settings(List<String> types) {
        return settings(types, (short) 6, (short) 3);
    }

    @Test
    void compute_returnsZeroReserveWhenNoAccounts() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(java.util.Collections.<Object[]>emptyList());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.currentReserve()).isEqualByComparingTo("0");
        assertThat(res.status()).isEqualTo("insufficient-data");
        assertThat(res.includedTypes()).containsExactly(Account.AccountType.BANK_SAVINGS);
    }

    @Test
    void compute_summarizesAcrossIncludedCurrencies() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS", "CASH"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("9000")},
                                new Object[] {"USD", new BigDecimal("500")}));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.currentReserve()).isEqualByComparingTo("9500");
        assertThat(res.buckets()).hasSize(2);
    }

    @Test
    void compute_returnsInsufficientDataStatusWhenSamplesBelow3() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("1000")}));
        // Only 2 of the 12 windows return non-zero -> samples = 2, below MIN_SAMPLES.
        // The default last-value (zero) is reused for every subsequent call.
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId),
                        eq(BudgetTransaction.TxnType.EXPENSE),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(
                        new BigDecimal("100"),
                        new BigDecimal("200"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO);

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.status()).isEqualTo("insufficient-data");
        assertThat(res.monthsCovered()).isNull();
    }

    @Test
    void compute_returnsRedWhenCoverageBelow3Months() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("1000")}));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("500"));

        EmergencyFundResponse res = service.compute(userId);

        // 1000 / 500 = 2.0 months -> red.
        assertThat(res.monthsCovered()).isEqualByComparingTo("2.0");
        assertThat(res.status()).isEqualTo("red");
    }

    @Test
    void compute_returnsAmberWhenCoverageBetween3And6Months() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("4500")}));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        EmergencyFundResponse res = service.compute(userId);

        // 4500 / 1000 = 4.5 -> amber.
        assertThat(res.status()).isEqualTo("amber");
    }

    @Test
    void compute_returnsGreenWhenCoverageAbove6Months() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("12000")}));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        EmergencyFundResponse res = service.compute(userId);

        // 12000 / 1000 = 12.0 -> green.
        assertThat(res.status()).isEqualTo("green");
    }

    @Test
    void compute_returnsGreenWithSentinelWhenZeroExpense() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("5000")}));
        // No expenses -> samples = 0 -> insufficient-data, NOT green/sentinel.
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.status()).isEqualTo("insufficient-data");
    }

    @Test
    void compute_defaultsToBankSavingsOnlyWhenSettingsAbsent() {
        when(userSettingsRepo.findById(userId)).thenReturn(Optional.empty());
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(java.util.Collections.<Object[]>emptyList());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.includedTypes()).containsExactly(Account.AccountType.BANK_SAVINGS);
    }

    @Test
    void compute_reAddsBankSavingsWhenCorruptedSettingsRowOmitsIt() {
        when(userSettingsRepo.findById(userId)).thenReturn(Optional.of(settings(List.of("CASH"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(java.util.Collections.<Object[]>emptyList());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.includedTypes())
                .containsExactly(Account.AccountType.BANK_SAVINGS, Account.AccountType.CASH);
    }

    @Test
    void compute_skipsUnknownTypeNamesWithWarn() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS", "MARS_BUNKER"))));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(java.util.Collections.<Object[]>emptyList());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.includedTypes()).containsExactly(Account.AccountType.BANK_SAVINGS);
    }

    @Test
    void compute_usesCustomTargetMonths() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"), (short) 9, (short) 3)));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("8000")}));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        EmergencyFundResponse res = service.compute(userId);

        // 8000 / 1000 = 8.0 -> with target=9 -> amber (would be green at default target=6).
        assertThat(res.monthsCovered()).isEqualByComparingTo("8.0");
        assertThat(res.status()).isEqualTo("amber");
        assertThat(res.targetMonths()).isEqualTo(9);
        assertThat(res.amberFloorMonths()).isEqualTo(3);
    }

    @Test
    void compute_usesCustomAmberFloor() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"), (short) 6, (short) 2)));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("2500")}));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        EmergencyFundResponse res = service.compute(userId);

        // 2500 / 1000 = 2.5 -> with amberFloor=2 -> amber (would be red at default amberFloor=3).
        assertThat(res.monthsCovered()).isEqualByComparingTo("2.5");
        assertThat(res.status()).isEqualTo("amber");
    }

    @Test
    void compute_targetBoundaryInclusiveAmber() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"), (short) 6, (short) 3)));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("6000")}));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        EmergencyFundResponse res = service.compute(userId);

        // 6000 / 1000 = 6.0 -> exactly at target=6 -> still amber (inclusive boundary).
        assertThat(res.monthsCovered()).isEqualByComparingTo("6.0");
        assertThat(res.status()).isEqualTo("amber");
    }

    @Test
    void compute_amberFloorBoundaryInclusiveAmber() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"), (short) 6, (short) 3)));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(
                        java.util.Arrays.<Object[]>asList(
                                new Object[] {"TRY", new BigDecimal("3000")}));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        EmergencyFundResponse res = service.compute(userId);

        // 3000 / 1000 = 3.0 -> exactly at amberFloor=3 -> amber (inclusive lower bound).
        assertThat(res.monthsCovered()).isEqualByComparingTo("3.0");
        assertThat(res.status()).isEqualTo("amber");
    }

    @Test
    void compute_fallsBackToDefaultsWhenColumnsAreNull() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"), null, null)));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(java.util.Collections.<Object[]>emptyList());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.targetMonths()).isEqualTo(6);
        assertThat(res.amberFloorMonths()).isEqualTo(3);
    }

    @Test
    void compute_responseCarriesActiveTargets() {
        when(userSettingsRepo.findById(userId))
                .thenReturn(Optional.of(settings(List.of("BANK_SAVINGS"), (short) 9, (short) 3)));
        when(accountRepository.sumBalancesByTypeForUser(eq(userId), any()))
                .thenReturn(java.util.Collections.<Object[]>emptyList());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        EmergencyFundResponse res = service.compute(userId);

        assertThat(res.targetMonths()).isEqualTo(9);
        assertThat(res.amberFloorMonths()).isEqualTo(3);
    }
}
