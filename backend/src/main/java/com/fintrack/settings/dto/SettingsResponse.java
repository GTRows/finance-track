package com.fintrack.settings.dto;

public record SettingsResponse(
        String currency,
        String language,
        String theme,
        String timezone,
        boolean onboardingCompleted
) {
}
