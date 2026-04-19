package com.fintrack.portfolio.dividend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordDividendRequest(
        @NotNull UUID assetId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal grossAmount,
        @PositiveOrZero BigDecimal withholdingTax,
        String currency,
        BigDecimal amountPerShare,
        BigDecimal shares,
        @NotNull LocalDate paymentDate,
        LocalDate exDividendDate,
        String notes
) {}
