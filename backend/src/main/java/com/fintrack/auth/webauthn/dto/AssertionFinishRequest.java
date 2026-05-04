package com.fintrack.auth.webauthn.dto;

import jakarta.validation.constraints.NotBlank;

/** Browser-supplied assertion payload completing a WebAuthn login ceremony. */
public record AssertionFinishRequest(
        @NotBlank String credentialId,
        @NotBlank String clientDataJSON,
        @NotBlank String authenticatorData,
        @NotBlank String signature,
        String userHandle) {}
