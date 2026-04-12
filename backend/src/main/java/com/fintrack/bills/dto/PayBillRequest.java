package com.fintrack.bills.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record PayBillRequest(
        @NotBlank String period,
        BigDecimal amount,
        String notes
) {
}
