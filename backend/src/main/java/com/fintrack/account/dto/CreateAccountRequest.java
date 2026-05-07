package com.fintrack.account.dto;

import com.fintrack.common.entity.Account;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Request body for creating a new account. */
public record CreateAccountRequest(
        @NotBlank(message = "Name is required")
                @Size(min = 1, max = 100, message = "Name must be 1-100 characters")
                String name,
        @NotNull(message = "Type is required") Account.AccountType type,
        @NotBlank(message = "Currency is required")
                @Pattern(
                        regexp = "^[A-Z]{3}$",
                        message = "Currency must be a 3-letter ISO code (e.g. TRY)")
                String currency,
        @Size(max = 100, message = "Institution must be at most 100 characters") String institution,
        @Size(max = 16, message = "Account number suffix must be at most 16 digits")
                @Pattern(
                        regexp = "^[0-9]{0,16}$",
                        message = "Account number suffix must be digits only")
                String accountNumberSuffix,
        @Size(max = 1000, message = "Notes must be at most 1000 characters") String notes,
        @DecimalMin(value = "-9999999999.99999999")
                @DecimalMax(value = "9999999999.99999999")
                @Digits(integer = 12, fraction = 8)
                BigDecimal openingBalance) {}
