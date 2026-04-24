package com.fintrack.settings;

import com.fintrack.common.entity.UserSettings;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.settings.dto.SettingsResponse;
import com.fintrack.settings.dto.UpdateSettingsRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserSettingsRepository repository;

    @Transactional(readOnly = true)
    public SettingsResponse get(UUID userId) {
        UserSettings settings =
                repository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Settings not found"));
        return toResponse(settings);
    }

    @Transactional
    public SettingsResponse update(UUID userId, UpdateSettingsRequest request) {
        UserSettings settings =
                repository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Settings not found"));

        if (request.currency() != null) {
            settings.setCurrency(request.currency());
        }
        if (request.language() != null) {
            settings.setLanguage(request.language());
        }
        if (request.theme() != null) {
            settings.setTheme(request.theme());
        }
        if (request.timezone() != null) {
            settings.setTimezone(request.timezone());
        }

        return toResponse(repository.save(settings));
    }

    @Transactional
    public SettingsResponse markOnboardingComplete(UUID userId) {
        UserSettings settings =
                repository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Settings not found"));
        if (!settings.isOnboardingCompleted()) {
            settings.setOnboardingCompleted(true);
            settings = repository.save(settings);
        }
        return toResponse(settings);
    }

    private SettingsResponse toResponse(UserSettings s) {
        return new SettingsResponse(
                s.getCurrency(),
                s.getLanguage(),
                s.getTheme(),
                s.getTimezone(),
                s.isOnboardingCompleted());
    }
}
