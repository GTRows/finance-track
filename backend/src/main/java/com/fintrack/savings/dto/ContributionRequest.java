package com.fintrack.savings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContributionRequest(
        @NotNull LocalDate contributionDate,
        @NotNull BigDecimal amount,
        @Size(max = 255) String note
) {}
