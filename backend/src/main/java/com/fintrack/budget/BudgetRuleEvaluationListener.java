package com.fintrack.budget;

import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.event.BudgetTransactionPersistedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs {@link BudgetRuleService#evaluateForTransaction(java.util.UUID, BudgetTransaction)} after
 * each persisted budget transaction. The listener swallows exceptions so a rule-evaluation failure
 * cannot disturb the writer's already-committed transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetRuleEvaluationListener {

    private final BudgetRuleService budgetRuleService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetTransactionPersisted(BudgetTransactionPersistedEvent event) {
        BudgetTransaction txn =
                BudgetTransaction.builder()
                        .id(event.transactionId())
                        .userId(event.userId())
                        .txnType(event.txnType())
                        .categoryId(event.categoryId())
                        .amount(event.amount())
                        .txnDate(event.txnDate())
                        .build();
        try {
            budgetRuleService.evaluateForTransaction(event.userId(), txn);
        } catch (Exception e) {
            log.warn(
                    "Budget rule evaluation failed for txn={}: {}",
                    event.transactionId(),
                    e.getMessage());
        }
    }
}
