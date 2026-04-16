package com.fintrack.budget.recurring.dto;

import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.RecurringTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringTemplateResponse(
        UUID id,
        BudgetTransaction.TxnType txnType,
        BigDecimal amount,
        UUID categoryId,
        String categoryName,
        String description,
        Integer dayOfMonth,
        boolean active,
        LocalDate lastMaterializedOn,
        LocalDate nextDueOn
) {
    public static RecurringTemplateResponse from(RecurringTemplate t, String categoryName, LocalDate nextDueOn) {
        return new RecurringTemplateResponse(
                t.getId(),
                t.getTxnType(),
                t.getAmount(),
                t.getCategoryId(),
                categoryName,
                t.getDescription(),
                t.getDayOfMonth(),
                t.isActive(),
                t.getLastMaterializedOn(),
                nextDueOn
        );
    }
}
