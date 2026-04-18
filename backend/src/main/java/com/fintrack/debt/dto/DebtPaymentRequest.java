package com.fintrack.debt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtPaymentRequest(
        @NotNull LocalDate paymentDate,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 255) String note
) {}
