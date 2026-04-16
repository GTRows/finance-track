package com.fintrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpDisableRequest(
        @NotBlank String password
) {
}
