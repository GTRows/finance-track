package com.fintrack.bills.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.UUID;

public record PayBillRequest(
        @NotBlank String period, BigDecimal amount, String notes, UUID accountId) {}
