package com.fintrack.auth.webauthn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Browser-supplied attestation payload completing a registration ceremony. */
public record RegistrationFinishRequest(
        @NotBlank String name,
        @NotBlank String credentialId,
        @NotBlank String clientDataJSON,
        @NotBlank String attestationObject,
        @NotNull List<String> transports) {}
