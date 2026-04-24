package com.fintrack.bills.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateBillRequest(
        @NotBlank String name,
        @Positive BigDecimal amount,
        @Min(1) @Max(31) int dueDay,
        String category,
        int remindDaysBefore,
        boolean autoPay,
        String notes) {}
