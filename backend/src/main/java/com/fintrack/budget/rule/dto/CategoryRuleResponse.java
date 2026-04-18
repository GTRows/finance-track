package com.fintrack.budget.rule.dto;

import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.TransactionCategoryRule;

import java.time.Instant;
import java.util.UUID;

public record CategoryRuleResponse(
        UUID id,
        String pattern,
        UUID categoryId,
        String categoryName,
        String categoryColor,
        BudgetTransaction.TxnType txnType,
        int priority,
        int matchCount,
        Instant createdAt
) {
    public static CategoryRuleResponse from(TransactionCategoryRule r, String categoryName, String categoryColor) {
        return new CategoryRuleResponse(
                r.getId(),
                r.getPattern(),
                r.getCategoryId(),
                categoryName,
                categoryColor,
                r.getTxnType(),
                r.getPriority(),
                r.getMatchCount(),
                r.getCreatedAt()
        );
    }
}
