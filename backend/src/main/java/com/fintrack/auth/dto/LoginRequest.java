package com.fintrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for user login.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
