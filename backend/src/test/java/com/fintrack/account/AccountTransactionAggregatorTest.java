package com.fintrack.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.InvestmentTransaction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AccountTransactionAggregator}. */
class AccountTransactionAggregatorTest {

    @Test
    void budgetDelta_returnsPositiveForIncome() {
        BigDecimal delta =
                AccountTransactionAggregator.budgetDelta(
                        BudgetTransaction.TxnType.INCOME, new BigDecimal("250"));
        assertThat(delta).isEqualByComparingTo("250");
    }

    @Test
    void budgetDelta_returnsNegativeForExpense() {
        BigDecimal delta =
                AccountTransactionAggregator.budgetDelta(
                        BudgetTransaction.TxnType.EXPENSE, new BigDecimal("75.50"));
        assertThat(delta).isEqualByComparingTo("-75.50");
    }

    @Test
    void budgetDelta_returnsZeroForNullAmount() {
        BigDecimal delta =
                AccountTransactionAggregator.budgetDelta(BudgetTransaction.TxnType.INCOME, null);
        assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void investmentDelta_buyReturnsNegated() {
        BigDecimal delta =
                AccountTransactionAggregator.investmentDelta(
                        InvestmentTransaction.TxnType.BUY, new BigDecimal("1000"));
        assertThat(delta).isEqualByComparingTo("-1000");
    }

    @Test
    void investmentDelta_sellReturnsPositive() {
        BigDecimal delta =
                AccountTransactionAggregator.investmentDelta(
                        InvestmentTransaction.TxnType.SELL, new BigDecimal("500"));
        assertThat(delta).isEqualByComparingTo("500");
    }

    @Test
    void investmentDelta_depositReturnsPositive() {
        BigDecimal delta =
                AccountTransactionAggregator.investmentDelta(
                        InvestmentTransaction.TxnType.DEPOSIT, new BigDecimal("300"));
        assertThat(delta).isEqualByComparingTo("300");
    }

    @Test
    void investmentDelta_withdrawReturnsNegated() {
        BigDecimal delta =
                AccountTransactionAggregator.investmentDelta(
                        InvestmentTransaction.TxnType.WITHDRAW, new BigDecimal("200"));
        assertThat(delta).isEqualByComparingTo("-200");
    }

    @Test
    void investmentDelta_besContributionReturnsNegated() {
        BigDecimal delta =
                AccountTransactionAggregator.investmentDelta(
                        InvestmentTransaction.TxnType.BES_CONTRIBUTION, new BigDecimal("400"));
        assertThat(delta).isEqualByComparingTo("-400");
    }

    @Test
    void investmentDelta_rebalanceReturnsZero() {
        BigDecimal delta =
                AccountTransactionAggregator.investmentDelta(
                        InvestmentTransaction.TxnType.REBALANCE, new BigDecimal("1000"));
        assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void investmentDelta_zeroForNullAmount() {
        BigDecimal delta =
                AccountTransactionAggregator.investmentDelta(
                        InvestmentTransaction.TxnType.BUY, null);
        assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void billPaymentDelta_pendingToPaidReturnsNegated() {
        BigDecimal delta =
                AccountTransactionAggregator.billPaymentDelta(
                        BillPayment.PaymentStatus.PENDING,
                        BillPayment.PaymentStatus.PAID,
                        new BigDecimal("100"));
        assertThat(delta).isEqualByComparingTo("-100");
    }

    @Test
    void billPaymentDelta_paidToPendingReturnsPositive() {
        BigDecimal delta =
                AccountTransactionAggregator.billPaymentDelta(
                        BillPayment.PaymentStatus.PAID,
                        BillPayment.PaymentStatus.PENDING,
                        new BigDecimal("100"));
        assertThat(delta).isEqualByComparingTo("100");
    }

    @Test
    void billPaymentDelta_paidToSkippedReturnsPositive() {
        BigDecimal delta =
                AccountTransactionAggregator.billPaymentDelta(
                        BillPayment.PaymentStatus.PAID,
                        BillPayment.PaymentStatus.SKIPPED,
                        new BigDecimal("100"));
        assertThat(delta).isEqualByComparingTo("100");
    }

    @Test
    void billPaymentDelta_pendingToPendingReturnsZero() {
        BigDecimal delta =
                AccountTransactionAggregator.billPaymentDelta(
                        BillPayment.PaymentStatus.PENDING,
                        BillPayment.PaymentStatus.PENDING,
                        new BigDecimal("100"));
        assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void billPaymentDelta_pendingToSkippedReturnsZero() {
        BigDecimal delta =
                AccountTransactionAggregator.billPaymentDelta(
                        BillPayment.PaymentStatus.PENDING,
                        BillPayment.PaymentStatus.SKIPPED,
                        new BigDecimal("100"));
        assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void billPaymentDelta_zeroForNullAmount() {
        BigDecimal delta =
                AccountTransactionAggregator.billPaymentDelta(
                        BillPayment.PaymentStatus.PENDING, BillPayment.PaymentStatus.PAID, null);
        assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
