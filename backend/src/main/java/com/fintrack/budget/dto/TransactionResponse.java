package com.fintrack.budget.dto;

import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.tag.TagService;

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
        BigDecimal originalAmount,
        String originalCurrency,
        UUID categoryId,
        String categoryName,
        String categoryColor,
        String description,
        LocalDate txnDate,
        boolean recurring,
        List<TagRef> tags,
        boolean hasReceipt,
        Instant createdAt
) {

    public record TagRef(UUID id, String name, String color) {
        public static TagRef from(TagService.TagSummary s) {
            return new TagRef(s.id(), s.name(), s.color());
        }
    }

    public static TransactionResponse from(BudgetTransaction t,
                                            String categoryName,
                                            String categoryColor,
                                            List<TagRef> tags) {
        return new TransactionResponse(
                t.getId(),
                t.getTxnType(),
                t.getAmount(),
                t.getCurrency(),
                t.getOriginalAmount(),
                t.getOriginalCurrency(),
                t.getCategoryId(),
                categoryName,
                categoryColor,
                t.getDescription(),
                t.getTxnDate(),
                t.isRecurring(),
                tags,
                t.getReceiptPath() != null && !t.getReceiptPath().isBlank(),
                t.getCreatedAt()
        );
    }
}
