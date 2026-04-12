package com.fintrack.budget.dto;

import com.fintrack.common.entity.BudgetTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        BudgetTransaction.TxnType txnType,
        BigDecimal amount,
        String currency,
        UUID categoryId,
        String categoryName,
        String categoryColor,
        String description,
        LocalDate txnDate,
        boolean recurring,
        List<String> tags,
        Instant createdAt
) {

    public static TransactionResponse from(BudgetTransaction t, String categoryName, String categoryColor) {
        return new TransactionResponse(
                t.getId(),
                t.getTxnType(),
                t.getAmount(),
                t.getCurrency(),
                t.getCategoryId(),
                categoryName,
                categoryColor,
                t.getDescription(),
                t.getTxnDate(),
                t.isRecurring(),
                t.getTags(),
                t.getCreatedAt()
        );
    }
}
