package com.fintrack.budget.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateCategoryRequest(
        @NotBlank String name,
        String icon,
        String color,
        BigDecimal budgetAmount
) {
}
