package com.fintrack.dashboard.dto;

import com.fintrack.common.entity.Account;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard tile rollup for the operator's emergency-fund coverage. {@code monthsCovered} is null
 * when there are fewer than three expense samples; the {@code status} value carries the band
 * descriptor ({@code red}, {@code amber}, {@code green}, or {@code insufficient-data}). The {@code
 * targetMonths} and {@code amberFloorMonths} fields surface the operator's configured band edges
 * (Phase 28 sub-plan 01) so the frontend can render dynamic copy.
 */
public record EmergencyFundResponse(
        BigDecimal currentReserve,
        List<CurrencyBucket> buckets,
        BigDecimal monthlyAverageExpense,
        BigDecimal monthsCovered,
        String status,
        List<Account.AccountType> includedTypes,
        int sampleMonths,
        int targetMonths,
        int amberFloorMonths) {

    public record CurrencyBucket(String currency, BigDecimal totalBalance) {}
}
