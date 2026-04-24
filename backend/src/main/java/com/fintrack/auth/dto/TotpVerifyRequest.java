package com.fintrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpVerifyRequest(@NotBlank String challengeToken, @NotBlank String code) {}
