package com.fintrack.savings.dto;

import com.fintrack.common.entity.SavingsGoalContribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContributionResponse(
        UUID id,
        LocalDate contributionDate,
        BigDecimal amount,
        String note
) {
    public static ContributionResponse from(SavingsGoalContribution c) {
        return new ContributionResponse(c.getId(), c.getContributionDate(), c.getAmount(), c.getNote());
    }
}
