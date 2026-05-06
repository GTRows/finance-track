package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.event.BudgetTransactionPersistedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetRuleEvaluationListenerTest {

    @Mock BudgetRuleService budgetRuleService;

    @InjectMocks BudgetRuleEvaluationListener listener;

    private final UUID userId = UUID.randomUUID();
    private final UUID txnId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    private BudgetTransactionPersistedEvent event() {
        return new BudgetTransactionPersistedEvent(
                userId,
                txnId,
                TxnType.EXPENSE,
                categoryId,
                new BigDecimal("125"),
                LocalDate.of(2026, 4, 10));
    }

    @Test
    void delegatesToBudgetRuleServiceWithEventFields() {
        listener.onBudgetTransactionPersisted(event());

        ArgumentCaptor<BudgetTransaction> captor = ArgumentCaptor.forClass(BudgetTransaction.class);
        verify(budgetRuleService).evaluateForTransaction(eq(userId), captor.capture());
        BudgetTransaction txn = captor.getValue();
        assertThat(txn.getId()).isEqualTo(txnId);
        assertThat(txn.getUserId()).isEqualTo(userId);
        assertThat(txn.getTxnType()).isEqualTo(TxnType.EXPENSE);
        assertThat(txn.getCategoryId()).isEqualTo(categoryId);
        assertThat(txn.getAmount()).isEqualByComparingTo("125");
        assertThat(txn.getTxnDate()).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    void evaluationExceptionIsSwallowed() {
        doThrow(new RuntimeException("rule store offline"))
                .when(budgetRuleService)
                .evaluateForTransaction(any(), any());

        assertThatCode(() -> listener.onBudgetTransactionPersisted(event()))
                .doesNotThrowAnyException();
    }
}
