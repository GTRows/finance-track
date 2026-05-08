package com.fintrack.common.event;

import com.fintrack.common.entity.BudgetTransaction;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published after {@code BudgetService.delete(...)} or {@code BudgetService.bulkDelete(...)}
 * commits. Subscribers run via {@code @TransactionalEventListener(phase = AFTER_COMMIT)} and
 * reverse the impact on the linked {@code accounts.current_balance}.
 */
public record BudgetTransactionDeletedEvent(
        UUID userId,
        UUID transactionId,
        BudgetTransaction.TxnType txnType,
        BigDecimal amount,
        UUID accountId) {}
