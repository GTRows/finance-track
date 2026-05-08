package com.fintrack.bills.dto;

import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record BillResponse(
        UUID id,
        String name,
        BigDecimal amount,
        String currency,
        String category,
        int dueDay,
        boolean active,
        boolean autoPay,
        int remindDaysBefore,
        String notes,
        String currentPeriodStatus,
        LocalDate currentPeriodDueDate,
        long daysUntilDue,
        LocalDate lastUsedOn,
        Long daysSinceLastUse,
        BillVarianceDto variance,
        UUID accountId,
        String accountName) {

    public static BillResponse from(Bill bill, BillPayment currentPayment) {
        return from(bill, currentPayment, null, null);
    }

    public static BillResponse from(
            Bill bill, BillPayment currentPayment, BillVarianceDto variance) {
        return from(bill, currentPayment, variance, null);
    }

    public static BillResponse from(
            Bill bill, BillPayment currentPayment, BillVarianceDto variance, String accountName) {
        LocalDate today = LocalDate.now();

        int dueDay = Math.min(bill.getDueDay(), today.lengthOfMonth());
        LocalDate dueDate = today.withDayOfMonth(dueDay);
        if (dueDate.isBefore(today)) {
            dueDate = dueDate.plusMonths(1);
            dueDate = dueDate.withDayOfMonth(Math.min(bill.getDueDay(), dueDate.lengthOfMonth()));
        }
        long daysUntil = ChronoUnit.DAYS.between(today, dueDate);

        String status = currentPayment != null ? currentPayment.getStatus().name() : "PENDING";

        LocalDate lastUsed = bill.getLastUsedOn();
        Long daysSinceLastUse = lastUsed == null ? null : ChronoUnit.DAYS.between(lastUsed, today);

        return new BillResponse(
                bill.getId(),
                bill.getName(),
                bill.getAmount(),
                bill.getCurrency(),
                bill.getCategory(),
                bill.getDueDay(),
                bill.isActive(),
                bill.isAutoPay(),
                bill.getRemindDaysBefore(),
                bill.getNotes(),
                status,
                dueDate,
                daysUntil,
                lastUsed,
                daysSinceLastUse,
                variance,
                currentPayment != null ? currentPayment.getAccountId() : null,
                accountName);
    }
}
