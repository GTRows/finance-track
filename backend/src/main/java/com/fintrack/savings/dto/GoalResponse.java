package com.fintrack.savings.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Denormalised savings goal with computed progress fields.
 *
 * <p>All percentage-style values are decimals (0.42 = 42%).
 */
public record GoalResponse(
        UUID id,
        String name,
        BigDecimal targetAmount,
        LocalDate targetDate,
        UUID linkedPortfolioId,
        String linkedPortfolioName,
        String notes,
        BigDecimal currentAmount,
        BigDecimal progressRatio,
        BigDecimal monthlyPace,
        LocalDate projectedCompletion,
        String status) {}
