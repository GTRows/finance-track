package com.fintrack.auth.webauthn.dto;

import jakarta.validation.constraints.NotBlank;

/** Username submitted to begin a WebAuthn assertion (login) ceremony. */
public record AssertionStartRequest(@NotBlank String username) {}
