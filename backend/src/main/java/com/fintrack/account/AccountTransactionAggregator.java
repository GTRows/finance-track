package com.fintrack.account;

import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.InvestmentTransaction;
import java.math.BigDecimal;

/**
 * Pure helpers that compute signed deltas to apply against {@code Account.currentBalance} when a
 * transaction-bearing row is inserted, updated, or deleted. The {@code AccountBalanceListener}
 * consumes these deltas after the writer commits.
 */
public final class AccountTransactionAggregator {

    private AccountTransactionAggregator() {}

    /**
     * Budget transaction net delta against the linked account. INCOME -&gt; +amount, EXPENSE -&gt;
     * -amount. Amount is in the user's home currency (BudgetService converts at write time).
     */
    public static BigDecimal budgetDelta(BudgetTransaction.TxnType txnType, BigDecimal amount) {
        if (amount == null || txnType == null) {
            return BigDecimal.ZERO;
        }
        return txnType == BudgetTransaction.TxnType.INCOME ? amount : amount.negate();
    }

    /**
     * Investment transaction net delta against the linked cash/bank account. BUY / BES_CONTRIBUTION
     * / WITHDRAW -&gt; -amountTry, SELL / DEPOSIT -&gt; +amountTry, REBALANCE -&gt; 0
     * (intra-portfolio reshuffle, no cash movement at this level).
     */
    public static BigDecimal investmentDelta(
            InvestmentTransaction.TxnType txnType, BigDecimal amountTry) {
        if (amountTry == null || txnType == null) {
            return BigDecimal.ZERO;
        }
        return switch (txnType) {
            case BUY, BES_CONTRIBUTION, WITHDRAW -> amountTry.negate();
            case SELL, DEPOSIT -> amountTry;
            case REBALANCE -> BigDecimal.ZERO;
        };
    }

    /**
     * Bill payment delta. A PENDING-&gt;PAID transition moves -amount; PAID-&gt;PENDING reverses
     * (+amount); PAID-&gt;SKIPPED reverses (+amount); any other transition is a no-op (returns
     * zero).
     */
    public static BigDecimal billPaymentDelta(
            BillPayment.PaymentStatus from, BillPayment.PaymentStatus to, BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (from == to) {
            return BigDecimal.ZERO;
        }
        // Forward path: pending/skipped -> paid moves -amount.
        if (to == BillPayment.PaymentStatus.PAID) {
            return amount.negate();
        }
        // Reversal path: paid -> pending/skipped moves +amount.
        if (from == BillPayment.PaymentStatus.PAID) {
            return amount;
        }
        return BigDecimal.ZERO;
    }
}
