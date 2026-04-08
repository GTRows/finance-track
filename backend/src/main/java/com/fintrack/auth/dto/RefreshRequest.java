package com.fintrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for token refresh and logout.
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
