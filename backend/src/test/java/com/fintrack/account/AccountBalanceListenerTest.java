package com.fintrack.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.event.BillPaidEvent;
import com.fintrack.common.event.BudgetTransactionDeletedEvent;
import com.fintrack.common.event.BudgetTransactionPersistedEvent;
import com.fintrack.common.event.InvestmentTransactionDeletedEvent;
import com.fintrack.common.event.InvestmentTransactionRecordedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class AccountBalanceListenerTest {

    @Mock AccountBalanceUpdater updater;

    @InjectMocks AccountBalanceListener listener;

    private final UUID userId = UUID.randomUUID();
    private final UUID txnId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID billId = UUID.randomUUID();
    private final UUID accountA = UUID.randomUUID();
    private final UUID accountB = UUID.randomUUID();

    @Test
    void onBudgetPersisted_appliesDeltaForNewIncome() {
        BudgetTransactionPersistedEvent event =
                new BudgetTransactionPersistedEvent(
                        userId,
                        txnId,
                        BudgetTransaction.TxnType.INCOME,
                        null,
                        new BigDecimal("500"),
                        LocalDate.of(2026, 4, 10),
                        accountA,
                        null);

        listener.onBudgetPersisted(event);

        verify(updater).apply(eq(accountA), eq(new BigDecimal("500")));
    }

    @Test
    void onBudgetPersisted_appliesNegativeDeltaForExpense() {
        BudgetTransactionPersistedEvent event =
                new BudgetTransactionPersistedEvent(
                        userId,
                        txnId,
                        BudgetTransaction.TxnType.EXPENSE,
                        null,
                        new BigDecimal("75"),
                        LocalDate.of(2026, 4, 10),
                        accountA,
                        null);

        listener.onBudgetPersisted(event);

        verify(updater).apply(eq(accountA), eq(new BigDecimal("-75")));
    }

    @Test
    void onBudgetPersisted_swapsAccountReversesPreviousAndAppliesCurrent() {
        BudgetTransactionPersistedEvent event =
                new BudgetTransactionPersistedEvent(
                        userId,
                        txnId,
                        BudgetTransaction.TxnType.EXPENSE,
                        null,
                        new BigDecimal("100"),
                        LocalDate.of(2026, 4, 10),
                        accountB,
                        accountA);

        listener.onBudgetPersisted(event);

        // Reverse on previous: undo -100 -> +100
        verify(updater).apply(eq(accountA), eq(new BigDecimal("100")));
        // Apply on new: -100
        verify(updater).apply(eq(accountB), eq(new BigDecimal("-100")));
    }

    @Test
    void onBudgetPersisted_skipsWhenBothAccountsNull() {
        BudgetTransactionPersistedEvent event =
                new BudgetTransactionPersistedEvent(
                        userId,
                        txnId,
                        BudgetTransaction.TxnType.INCOME,
                        null,
                        new BigDecimal("100"),
                        LocalDate.of(2026, 4, 10),
                        null,
                        null);

        listener.onBudgetPersisted(event);

        verifyNoInteractions(updater);
    }

    @Test
    void onBudgetPersisted_swallowsRuntimeException() {
        doThrow(new RuntimeException("boom")).when(updater).apply(any(), any());
        BudgetTransactionPersistedEvent event =
                new BudgetTransactionPersistedEvent(
                        userId,
                        txnId,
                        BudgetTransaction.TxnType.INCOME,
                        null,
                        new BigDecimal("100"),
                        LocalDate.of(2026, 4, 10),
                        accountA,
                        null);

        // Should not propagate.
        listener.onBudgetPersisted(event);
        verify(updater, times(1)).apply(any(), any());
    }

    @Test
    void onBudgetDeleted_reversesDelta() {
        BudgetTransactionDeletedEvent event =
                new BudgetTransactionDeletedEvent(
                        userId,
                        txnId,
                        BudgetTransaction.TxnType.EXPENSE,
                        new BigDecimal("250"),
                        accountA);

        listener.onBudgetDeleted(event);

        // EXPENSE -> -250; deletion reverses to +250.
        verify(updater).apply(eq(accountA), eq(new BigDecimal("250")));
    }

    @Test
    void onBudgetDeleted_skipsWhenAccountIdNull() {
        BudgetTransactionDeletedEvent event =
                new BudgetTransactionDeletedEvent(
                        userId,
                        txnId,
                        BudgetTransaction.TxnType.INCOME,
                        new BigDecimal("100"),
                        null);

        listener.onBudgetDeleted(event);

        verifyNoInteractions(updater);
    }

    @Test
    void onInvestmentRecorded_appliesNegativeForBuy() {
        InvestmentTransactionRecordedEvent event =
                new InvestmentTransactionRecordedEvent(
                        userId,
                        portfolioId,
                        assetId,
                        txnId,
                        InvestmentTransaction.TxnType.BUY,
                        new BigDecimal("2"),
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        accountA,
                        null);

        listener.onInvestmentRecorded(event);

        // 2 * 100 = 200; BUY -> -200.
        verify(updater).apply(eq(accountA), eq(new BigDecimal("-200")));
    }

    @Test
    void onInvestmentRecorded_appliesPositiveForSell() {
        InvestmentTransactionRecordedEvent event =
                new InvestmentTransactionRecordedEvent(
                        userId,
                        portfolioId,
                        assetId,
                        txnId,
                        InvestmentTransaction.TxnType.SELL,
                        new BigDecimal("1"),
                        new BigDecimal("300"),
                        BigDecimal.ZERO,
                        accountA,
                        null);

        listener.onInvestmentRecorded(event);

        verify(updater).apply(eq(accountA), eq(new BigDecimal("300")));
    }

    @Test
    void onInvestmentRecorded_zeroForRebalance() {
        InvestmentTransactionRecordedEvent event =
                new InvestmentTransactionRecordedEvent(
                        userId,
                        portfolioId,
                        assetId,
                        txnId,
                        InvestmentTransaction.TxnType.REBALANCE,
                        new BigDecimal("1"),
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        accountA,
                        null);

        listener.onInvestmentRecorded(event);

        verify(updater).apply(eq(accountA), eq(BigDecimal.ZERO));
    }

    @Test
    void onInvestmentRecorded_swapsAccountOnEdit() {
        InvestmentTransactionRecordedEvent event =
                new InvestmentTransactionRecordedEvent(
                        userId,
                        portfolioId,
                        assetId,
                        txnId,
                        InvestmentTransaction.TxnType.BUY,
                        new BigDecimal("2"),
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        accountB,
                        accountA);

        listener.onInvestmentRecorded(event);

        // Reverse on previous: BUY -> -200; reverse = +200.
        verify(updater).apply(eq(accountA), eq(new BigDecimal("200")));
        verify(updater).apply(eq(accountB), eq(new BigDecimal("-200")));
    }

    @Test
    void onInvestmentDeleted_reversesDelta() {
        InvestmentTransactionDeletedEvent event =
                new InvestmentTransactionDeletedEvent(
                        userId,
                        portfolioId,
                        assetId,
                        txnId,
                        InvestmentTransaction.TxnType.BUY,
                        new BigDecimal("2"),
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        accountA);

        listener.onInvestmentDeleted(event);

        verify(updater).apply(eq(accountA), eq(new BigDecimal("200")));
    }

    @Test
    void onBillPaid_appliesNegativeOnFirstPayment() {
        BillPaidEvent event =
                new BillPaidEvent(
                        userId,
                        billId,
                        "Electric",
                        "2026-04",
                        new BigDecimal("180"),
                        "TRY",
                        Instant.now(),
                        accountA,
                        BillPayment.PaymentStatus.PENDING,
                        BigDecimal.ZERO,
                        null);

        listener.onBillPaid(event);

        verify(updater).apply(eq(accountA), eq(new BigDecimal("-180")));
    }

    @Test
    void onBillPaid_reversesPreviousAccountWhenAccountChanged() {
        BillPaidEvent event =
                new BillPaidEvent(
                        userId,
                        billId,
                        "Electric",
                        "2026-04",
                        new BigDecimal("180"),
                        "TRY",
                        Instant.now(),
                        accountB,
                        BillPayment.PaymentStatus.PAID,
                        new BigDecimal("180"),
                        accountA);

        listener.onBillPaid(event);

        // Reverse on previous A: +previousAmount.
        verify(updater).apply(eq(accountA), eq(new BigDecimal("180")));
        // Status PAID -> PAID on new account is a zero delta (updater short-circuits).
        verify(updater).apply(eq(accountB), eq(BigDecimal.ZERO));
    }

    @Test
    void onBillPaid_zeroDeltaWhenStatusUnchanged() {
        BillPaidEvent event =
                new BillPaidEvent(
                        userId,
                        billId,
                        "Electric",
                        "2026-04",
                        new BigDecimal("180"),
                        "TRY",
                        Instant.now(),
                        accountA,
                        BillPayment.PaymentStatus.PAID,
                        new BigDecimal("180"),
                        accountA);

        listener.onBillPaid(event);

        // Same account, PAID -> PAID -> aggregator returns zero, updater short-circuits at zero.
        // We assert the apply was called exactly once with zero on accountA.
        verify(updater).apply(eq(accountA), eq(BigDecimal.ZERO));
    }
}
