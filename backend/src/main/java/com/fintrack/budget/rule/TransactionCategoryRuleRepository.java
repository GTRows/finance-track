package com.fintrack.budget.rule;

import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.TransactionCategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionCategoryRuleRepository extends JpaRepository<TransactionCategoryRule, UUID> {

    List<TransactionCategoryRule> findByUserIdAndTxnTypeOrderByPriorityAscCreatedAtAsc(
            UUID userId, BudgetTransaction.TxnType txnType);

    List<TransactionCategoryRule> findByUserIdOrderByPriorityAscCreatedAtAsc(UUID userId);

    Optional<TransactionCategoryRule> findByIdAndUserId(UUID id, UUID userId);
}
