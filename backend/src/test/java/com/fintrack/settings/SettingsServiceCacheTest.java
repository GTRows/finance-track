package com.fintrack.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditService;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.settings.dto.SettingsResponse;
import com.fintrack.settings.dto.UpdateSettingsRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(SettingsServiceCacheTest.TestConfig.class)
class SettingsServiceCacheTest {

    @EnableCaching
    @Import({CacheConfig.class, SettingsService.class})
    static class TestConfig {}

    @MockBean UserSettingsRepository repository;
    @MockBean AuditService auditService;

    @Autowired SettingsService service;
    @Autowired CacheManager cacheManager;

    private final UUID userId = UUID.randomUUID();

    private UserSettings defaults(UUID id) {
        return UserSettings.builder()
                .userId(id)
                .currency("TRY")
                .language("tr")
                .theme("dark")
                .timezone("Europe/Istanbul")
                .onboardingCompleted(false)
                .build();
    }

    @BeforeEach
    void clearCaches() {
        cacheManager.getCache(CacheConfig.USER_SETTINGS_CACHE).clear();
    }

    @Test
    void getSecondCallServedFromCache() {
        when(repository.findById(userId)).thenReturn(Optional.of(defaults(userId)));

        service.get(userId);
        service.get(userId);

        verify(repository, times(1)).findById(userId);
    }

    @Test
    void differentUsersHaveSeparateCacheEntries() {
        UUID other = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.of(defaults(userId)));
        when(repository.findById(other)).thenReturn(Optional.of(defaults(other)));

        service.get(userId);
        service.get(other);
        service.get(userId);
        service.get(other);

        verify(repository, times(1)).findById(userId);
        verify(repository, times(1)).findById(other);
    }

    @Test
    void updateWritesFreshValueIntoCacheWithoutExtraReadOnNextGet() {
        UserSettings existing = defaults(userId);
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        SettingsResponse updated =
                service.update(userId, new UpdateSettingsRequest("USD", null, null, null));
        SettingsResponse cached = service.get(userId);

        assertThat(updated.currency()).isEqualTo("USD");
        assertThat(cached.currency()).isEqualTo("USD");
        // findById fired once for the update, zero times for the get (CachePut wrote the entry).
        verify(repository, times(1)).findById(userId);
    }

    @Test
    void markOnboardingCompleteWritesFreshValueIntoCache() {
        UserSettings existing = defaults(userId);
        when(repository.findById(userId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        SettingsResponse onboarded = service.markOnboardingComplete(userId);
        SettingsResponse cached = service.get(userId);

        assertThat(onboarded.onboardingCompleted()).isTrue();
        assertThat(cached.onboardingCompleted()).isTrue();
        verify(repository, times(1)).findById(userId);
    }

    @Test
    void resourceNotFoundExceptionIsNotCached() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.get(userId)).isInstanceOf(ResourceNotFoundException.class);

        // Second call retried the repo because the throw was not cached.
        verify(repository, times(2)).findById(userId);
    }
}
