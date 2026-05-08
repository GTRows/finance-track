package com.fintrack.dashboard.dto;

import com.fintrack.common.entity.Account;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard tile rollup for the operator's emergency-fund coverage. {@code monthsCovered} is null
 * when there are fewer than three expense samples; the {@code status} value carries the band
 * descriptor ({@code red}, {@code amber}, {@code green}, or {@code insufficient-data}).
 */
public record EmergencyFundResponse(
        BigDecimal currentReserve,
        List<CurrencyBucket> buckets,
        BigDecimal monthlyAverageExpense,
        BigDecimal monthsCovered,
        String status,
        List<Account.AccountType> includedTypes,
        int sampleMonths) {

    public record CurrencyBucket(String currency, BigDecimal totalBalance) {}
}
