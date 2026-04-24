package com.fintrack.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.UserSettings;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.settings.dto.SettingsResponse;
import com.fintrack.settings.dto.UpdateSettingsRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock UserSettingsRepository repository;

    @InjectMocks SettingsService service;

    private final UUID userId = UUID.randomUUID();

    private UserSettings defaults() {
        return UserSettings.builder()
                .userId(userId)
                .currency("TRY")
                .language("tr")
                .theme("dark")
                .timezone("Europe/Istanbul")
                .onboardingCompleted(false)
                .build();
    }

    @Test
    void getThrowsWhenSettingsMissing() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getReturnsFieldsAsResponse() {
        when(repository.findById(userId)).thenReturn(Optional.of(defaults()));

        SettingsResponse res = service.get(userId);

        assertThat(res.currency()).isEqualTo("TRY");
        assertThat(res.language()).isEqualTo("tr");
        assertThat(res.theme()).isEqualTo("dark");
        assertThat(res.timezone()).isEqualTo("Europe/Istanbul");
        assertThat(res.onboardingCompleted()).isFalse();
    }

    @Test
    void updateThrowsWhenSettingsMissing() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId, new UpdateSettingsRequest("USD", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateAppliesOnlyNonNullFields() {
        UserSettings existing = defaults();
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.update(userId, new UpdateSettingsRequest("USD", null, "light", null));

        assertThat(existing.getCurrency()).isEqualTo("USD");
        assertThat(existing.getLanguage()).isEqualTo("tr");
        assertThat(existing.getTheme()).isEqualTo("light");
        assertThat(existing.getTimezone()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void updateReplacesAllFieldsWhenAllProvided() {
        UserSettings existing = defaults();
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        SettingsResponse res =
                service.update(
                        userId, new UpdateSettingsRequest("EUR", "en", "system", "Europe/Berlin"));

        assertThat(existing.getCurrency()).isEqualTo("EUR");
        assertThat(existing.getLanguage()).isEqualTo("en");
        assertThat(existing.getTheme()).isEqualTo("system");
        assertThat(existing.getTimezone()).isEqualTo("Europe/Berlin");
        assertThat(res.currency()).isEqualTo("EUR");
    }

    @Test
    void updateWithAllNullsLeavesSettingsUnchanged() {
        UserSettings existing = defaults();
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.update(userId, new UpdateSettingsRequest(null, null, null, null));

        assertThat(existing.getCurrency()).isEqualTo("TRY");
        assertThat(existing.getLanguage()).isEqualTo("tr");
    }

    @Test
    void markOnboardingCompleteSetsFlagAndPersists() {
        UserSettings existing = defaults();
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        SettingsResponse res = service.markOnboardingComplete(userId);

        assertThat(existing.isOnboardingCompleted()).isTrue();
        assertThat(res.onboardingCompleted()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    void markOnboardingCompleteDoesNotWriteWhenAlreadyDone() {
        UserSettings existing = defaults();
        existing.setOnboardingCompleted(true);
        when(repository.findById(userId)).thenReturn(Optional.of(existing));

        service.markOnboardingComplete(userId);

        verify(repository, never()).save(any());
    }

    @Test
    void markOnboardingCompleteThrowsWhenSettingsMissing() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markOnboardingComplete(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
