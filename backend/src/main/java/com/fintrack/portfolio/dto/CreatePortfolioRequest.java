package com.fintrack.portfolio.dto;

import com.fintrack.common.entity.Portfolio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new portfolio.
 */
public record CreatePortfolioRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 100, message = "Name must be 1-100 characters")
        String name,

        @NotNull(message = "Type is required")
        Portfolio.PortfolioType type,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description
) {
}
