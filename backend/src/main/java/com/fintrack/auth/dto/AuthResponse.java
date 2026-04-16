package com.fintrack.auth.dto;

/**
 * Response returned after successful authentication (login, register, refresh).
 * When {@code requiresTotp} is true the token fields are null; the client
 * must submit the TOTP code along with the {@code totpChallengeToken} via
 * /auth/2fa/verify to complete the flow.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long accessExpiresIn,
        UserProfileResponse user,
        boolean requiresTotp,
        String totpChallengeToken
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserProfileResponse user) {
        return new AuthResponse(accessToken, refreshToken, expiresIn, user, false, null);
    }

    public static AuthResponse challenge(String totpChallengeToken) {
        return new AuthResponse(null, null, 0L, null, true, totpChallengeToken);
    }
}
