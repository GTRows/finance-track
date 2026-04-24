package com.fintrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Alternative to {@link TotpVerifyRequest} when the user has lost their authenticator and needs to
 * redeem one of the pre-shared recovery codes.
 */
public record TotpRecoveryVerifyRequest(
        @NotBlank String challengeToken, @NotBlank String recoveryCode) {}
