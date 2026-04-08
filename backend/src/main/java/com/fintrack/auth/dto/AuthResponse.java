package com.fintrack.auth.dto;

/**
 * Response returned after successful authentication (login, register, refresh).
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long accessExpiresIn,
        UserProfileResponse user
) {
}
