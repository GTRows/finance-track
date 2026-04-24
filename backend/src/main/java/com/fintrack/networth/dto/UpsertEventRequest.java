package com.fintrack.networth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertEventRequest(
        @NotNull LocalDate eventDate,
        @NotBlank @Size(max = 20) String eventType,
        @NotBlank @Size(max = 120) String label,
        @Size(max = 2000) String note,
        BigDecimal impactTry) {}
