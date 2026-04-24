package com.fintrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpEnableRequest(@NotBlank String code) {}
