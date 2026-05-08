package com.fintrack.common.event;

import com.fintrack.common.entity.BudgetTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after {@code BudgetService.create(...)} and {@code BudgetService.update(...)} commit.
 * Subscribers run via {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 *
 * <p>{@code accountId} is the linked account at commit time (may be null when the operator did not
 * pick one). {@code previousAccountId} is the prior account on update flows so the
 * AccountBalanceListener can reverse the delta on the previous account before applying it on the
 * new one. {@code previousAccountId} is null on create paths.
 */
public record BudgetTransactionPersistedEvent(
        UUID userId,
        UUID transactionId,
        BudgetTransaction.TxnType txnType,
        UUID categoryId,
        BigDecimal amount,
        LocalDate txnDate,
        UUID accountId,
        UUID previousAccountId) {}
