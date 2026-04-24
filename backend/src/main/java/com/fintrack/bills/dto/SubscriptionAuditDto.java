package com.fintrack.bills.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Candidate subscriptions flagged as stale for user review. */
public record SubscriptionAuditDto(
        BigDecimal totalMonthlySpend,
        BigDecimal potentialMonthlySavings,
        int candidateCount,
        List<Candidate> candidates) {
    public record Candidate(
            UUID billId,
            String name,
            String category,
            BigDecimal amount,
            String currency,
            LocalDate lastUsedOn,
            Long daysSinceLastUse,
            String reason) {}
}
