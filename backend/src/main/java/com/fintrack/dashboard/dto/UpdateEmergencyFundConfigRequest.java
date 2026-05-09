package com.fintrack.dashboard.dto;

import com.fintrack.common.entity.Account;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code PUT /api/v1/dashboard/emergency-fund/config}. The list must contain
 * {@link Account.AccountType#BANK_SAVINGS}; service-layer validation enforces that. The cross-field
 * invariant {@code amberFloorMonths < targetMonths} is also enforced at the service layer (Bean
 * Validation has no clean cross-field annotation).
 */
public record UpdateEmergencyFundConfigRequest(
        @NotNull @Size(min = 1, max = 6, message = "Must include between 1 and 6 account types")
                List<Account.AccountType> types,
        @NotNull @Min(2) @Max(24) Integer targetMonths,
        @NotNull @Min(1) @Max(23) Integer amberFloorMonths) {}
