package com.fintrack.common.event;

import com.fintrack.common.entity.BudgetTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after {@code BudgetService.create(...)} and {@code BudgetService.update(...)} commit.
 * Subscribers run via {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public record BudgetTransactionPersistedEvent(
        UUID userId,
        UUID transactionId,
        BudgetTransaction.TxnType txnType,
        UUID categoryId,
        BigDecimal amount,
        LocalDate txnDate) {}
