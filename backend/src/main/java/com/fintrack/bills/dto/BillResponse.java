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
        long daysUntilDue
) {

    public static BillResponse from(Bill bill, BillPayment currentPayment) {
        LocalDate today = LocalDate.now();
        String period = today.getYear() + "-" + String.format("%02d", today.getMonthValue());

        int dueDay = Math.min(bill.getDueDay(), today.lengthOfMonth());
        LocalDate dueDate = today.withDayOfMonth(dueDay);
        if (dueDate.isBefore(today)) {
            dueDate = dueDate.plusMonths(1);
            dueDate = dueDate.withDayOfMonth(Math.min(bill.getDueDay(), dueDate.lengthOfMonth()));
        }
        long daysUntil = ChronoUnit.DAYS.between(today, dueDate);

        String status = currentPayment != null
                ? currentPayment.getStatus().name()
                : "PENDING";

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
                daysUntil
        );
    }
}
