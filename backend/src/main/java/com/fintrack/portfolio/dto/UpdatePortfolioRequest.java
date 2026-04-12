package com.fintrack.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an existing portfolio.
 */
public record UpdatePortfolioRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 100, message = "Name must be 1-100 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description
) {
}
