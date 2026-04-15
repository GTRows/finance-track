package com.fintrack.portfolio.transaction.dto;

import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordTransactionRequest(
        @NotNull(message = "Asset is required")
        UUID assetId,

        @NotNull(message = "Transaction type is required")
        TxnType txnType,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.00000001", message = "Quantity must be greater than zero")
        @Digits(integer = 12, fraction = 8, message = "Quantity must have at most 12 integer and 8 fractional digits")
        BigDecimal quantity,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Unit price cannot be negative")
        @Digits(integer = 16, fraction = 4, message = "Unit price must have at most 16 integer and 4 fractional digits")
        BigDecimal priceTry,

        @DecimalMin(value = "0.0", inclusive = true, message = "Fee cannot be negative")
        @Digits(integer = 16, fraction = 4, message = "Fee must have at most 16 integer and 4 fractional digits")
        BigDecimal feeTry,

        @NotNull(message = "Transaction date is required")
        LocalDate txnDate,

        @Size(max = 500, message = "Notes must be at most 500 characters")
        String notes
) {
}
