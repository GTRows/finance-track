package com.fintrack.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateRebalanceThresholdRequest(
        @NotNull @DecimalMin("0.10") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
                BigDecimal threshold) {}
