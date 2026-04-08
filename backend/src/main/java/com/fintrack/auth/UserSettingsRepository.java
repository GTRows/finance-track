package com.fintrack.auth;

import com.fintrack.common.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for per-user preference settings.
 */
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
