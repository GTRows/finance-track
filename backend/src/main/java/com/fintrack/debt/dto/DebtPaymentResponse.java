package com.fintrack.debt.dto;

import com.fintrack.common.entity.DebtPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DebtPaymentResponse(
        UUID id,
        LocalDate paymentDate,
        BigDecimal amount,
        String note
) {
    public static DebtPaymentResponse from(DebtPayment p) {
        return new DebtPaymentResponse(p.getId(), p.getPaymentDate(), p.getAmount(), p.getNote());
    }
}
