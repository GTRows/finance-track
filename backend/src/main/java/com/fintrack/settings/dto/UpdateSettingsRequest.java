package com.fintrack.settings.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSettingsRequest(
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        String currency,

        @Pattern(regexp = "^(tr|en)$", message = "Language must be 'tr' or 'en'")
        String language,

        @Pattern(regexp = "^(light|dark|system)$", message = "Theme must be 'light', 'dark', or 'system'")
        String theme,

        @Size(max = 64)
        String timezone
) {
}
