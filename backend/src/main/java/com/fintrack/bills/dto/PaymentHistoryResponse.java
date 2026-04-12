package com.fintrack.bills.dto;

import com.fintrack.common.entity.BillPayment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentHistoryResponse(
        String period,
        BigDecimal amount,
        String status,
        Instant paidAt,
        String notes
) {

    public static PaymentHistoryResponse from(BillPayment p) {
        return new PaymentHistoryResponse(
                p.getPeriod(),
                p.getAmount(),
                p.getStatus().name(),
                p.getPaidAt(),
                p.getNotes()
        );
    }
}
