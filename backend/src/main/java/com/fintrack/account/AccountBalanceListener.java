package com.fintrack.account;

import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.event.BillPaidEvent;
import com.fintrack.common.event.BudgetTransactionDeletedEvent;
import com.fintrack.common.event.BudgetTransactionPersistedEvent;
import com.fintrack.common.event.InvestmentTransactionDeletedEvent;
import com.fintrack.common.event.InvestmentTransactionRecordedEvent;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recomputes Account.currentBalance after a transaction-bearing row commits. Mirrors
 * HoldingProjectionListener (25-01): one method per event type, all
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}. Failures are logged (cannot rollback
 * the writer). All work delegates to {@link AccountBalanceUpdater} so REQUIRES_NEW is honoured.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountBalanceListener {

    private final AccountBalanceUpdater updater;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetPersisted(BudgetTransactionPersistedEvent event) {
        try {
            // Reverse delta on previous account (if account changed), then apply on current.
            if (event.previousAccountId() != null
                    && !event.previousAccountId().equals(event.accountId())) {
                BigDecimal reverse =
                        AccountTransactionAggregator.budgetDelta(event.txnType(), event.amount())
                                .negate();
                updater.apply(event.previousAccountId(), reverse);
            }
            if (event.accountId() != null) {
                BigDecimal delta =
                        AccountTransactionAggregator.budgetDelta(event.txnType(), event.amount());
                updater.apply(event.accountId(), delta);
            }
        } catch (RuntimeException e) {
            log.error(
                    "Budget balance rollup failed: txn={} accountId={}: {}",
                    event.transactionId(),
                    event.accountId(),
                    e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetDeleted(BudgetTransactionDeletedEvent event) {
        try {
            if (event.accountId() == null) {
                return;
            }
            BigDecimal reverse =
                    AccountTransactionAggregator.budgetDelta(event.txnType(), event.amount())
                            .negate();
            updater.apply(event.accountId(), reverse);
        } catch (RuntimeException e) {
            log.error(
                    "Budget delete rollup failed: txn={} accountId={}: {}",
                    event.transactionId(),
                    event.accountId(),
                    e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvestmentRecorded(InvestmentTransactionRecordedEvent event) {
        try {
            BigDecimal amountTry = computeAmountTry(event.priceTry(), event.quantity());
            if (event.previousAccountId() != null
                    && !event.previousAccountId().equals(event.accountId())) {
                BigDecimal reverse =
                        AccountTransactionAggregator.investmentDelta(event.txnType(), amountTry)
                                .negate();
                updater.apply(event.previousAccountId(), reverse);
            }
            if (event.accountId() != null) {
                BigDecimal delta =
                        AccountTransactionAggregator.investmentDelta(event.txnType(), amountTry);
                updater.apply(event.accountId(), delta);
            }
        } catch (RuntimeException e) {
            log.error(
                    "Investment balance rollup failed: txn={} accountId={}: {}",
                    event.transactionId(),
                    event.accountId(),
                    e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvestmentDeleted(InvestmentTransactionDeletedEvent event) {
        try {
            if (event.accountId() == null) {
                return;
            }
            BigDecimal amountTry = computeAmountTry(event.priceTry(), event.quantity());
            BigDecimal reverse =
                    AccountTransactionAggregator.investmentDelta(event.txnType(), amountTry)
                            .negate();
            updater.apply(event.accountId(), reverse);
        } catch (RuntimeException e) {
            log.error(
                    "Investment delete rollup failed: txn={} accountId={}: {}",
                    event.transactionId(),
                    event.accountId(),
                    e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBillPaid(BillPaidEvent event) {
        try {
            // If the operator switched accounts AND the previous payment was PAID, reverse the
            // prior -amount on the previous account.
            if (event.previousAccountId() != null
                    && !Objects.equals(event.previousAccountId(), event.accountId())
                    && event.previousStatus() == BillPayment.PaymentStatus.PAID) {
                BigDecimal previousAmount =
                        event.previousAmount() != null ? event.previousAmount() : BigDecimal.ZERO;
                updater.apply(event.previousAccountId(), previousAmount);
            }
            if (event.accountId() != null) {
                BigDecimal delta =
                        AccountTransactionAggregator.billPaymentDelta(
                                event.previousStatus(),
                                BillPayment.PaymentStatus.PAID,
                                event.amount());
                updater.apply(event.accountId(), delta);
            }
        } catch (RuntimeException e) {
            log.error(
                    "Bill payment rollup failed: bill={} accountId={}: {}",
                    event.billId(),
                    event.accountId(),
                    e.getMessage());
        }
    }

    private static BigDecimal computeAmountTry(BigDecimal priceTry, BigDecimal quantity) {
        if (priceTry == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return priceTry.multiply(quantity);
    }
}
