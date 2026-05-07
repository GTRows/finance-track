package com.fintrack.account.dto;

import com.fintrack.common.entity.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Account response sent to the client. */
public record AccountResponse(
        UUID id,
        String name,
        Account.AccountType type,
        String currency,
        String institution,
        String accountNumberSuffix,
        String notes,
        BigDecimal currentBalance,
        boolean archived,
        Instant createdAt) {

    /** Maps an {@link Account} entity to a response DTO. */
    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getId(),
                a.getName(),
                a.getAccountType(),
                a.getCurrency(),
                a.getInstitution(),
                a.getAccountNumberSuffix(),
                a.getNotes(),
                a.getCurrentBalance(),
                a.isArchived(),
                a.getCreatedAt());
    }
}
