package com.fintrack.debt.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertDebtRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 20) String debtType,
        @NotNull @DecimalMin("0.01") BigDecimal principal,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal annualRate,
        @NotNull @Min(1) @Max(720) Integer termMonths,
        @NotNull LocalDate startDate,
        @Size(max = 2000) String notes
) {}
