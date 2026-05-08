package com.fintrack.dashboard.dto;

import com.fintrack.common.entity.Account;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code PUT /api/v1/dashboard/emergency-fund/types}. The list must contain {@link
 * Account.AccountType#BANK_SAVINGS}; service-layer validation enforces that.
 */
public record UpdateEmergencyFundTypesRequest(
        @NotNull @Size(min = 1, max = 6, message = "Must include between 1 and 6 account types")
                List<Account.AccountType> types) {}
