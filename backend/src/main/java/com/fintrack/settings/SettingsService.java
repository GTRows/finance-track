package com.fintrack.settings;

import static com.fintrack.audit.AuditAction.USER_SETTINGS_EMERGENCY_FUND_UPDATED;
import static com.fintrack.audit.AuditAction.USER_SETTINGS_REBALANCE_THRESHOLD_UPDATED;

import com.fintrack.audit.AuditService;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.settings.dto.SettingsResponse;
import com.fintrack.settings.dto.UpdateSettingsRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    static final int MIN_TARGET = 2;
    static final int MAX_TARGET = 24;
    static final int MIN_AMBER_FLOOR = 1;
    static final BigDecimal MIN_REBALANCE_THRESHOLD = new BigDecimal("0.10");
    static final BigDecimal MAX_REBALANCE_THRESHOLD = new BigDecimal("10.00");

    private final UserSettingsRepository repository;
    private final AuditService auditService;

    @Cacheable(value = CacheConfig.USER_SETTINGS_CACHE, key = "#userId")
    @Transactional(readOnly = true)
    public SettingsResponse get(UUID userId) {
        UserSettings settings =
                repository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Settings not found"));
        return toResponse(settings);
    }

    @CachePut(value = CacheConfig.USER_SETTINGS_CACHE, key = "#userId")
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

    @CachePut(value = CacheConfig.USER_SETTINGS_CACHE, key = "#userId")
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

    /**
     * Persists the emergency-fund account-type inclusion list. {@link
     * Account.AccountType#BANK_SAVINGS} must be in the list; otherwise a {@link
     * BusinessRuleException} with code {@code EMERGENCY_FUND_BANK_SAVINGS_REQUIRED} is thrown.
     *
     * <p>This method delegates to {@link #updateEmergencyFundConfig(UUID, List, int, int)} reusing
     * the user's existing target / amber-floor values, preserving the legacy single-shot types-only
     * contract.
     */
    @Transactional
    public void updateEmergencyFundTypes(UUID userId, List<Account.AccountType> types) {
        UserSettings current =
                repository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Settings not found"));
        int target =
                current.getEmergencyFundTargetMonths() != null
                        ? current.getEmergencyFundTargetMonths()
                        : 6;
        int amberFloor =
                current.getEmergencyFundAmberFloorMonths() != null
                        ? current.getEmergencyFundAmberFloorMonths()
                        : 3;
        updateEmergencyFundConfig(userId, types, target, amberFloor);
    }

    /**
     * Persists the full emergency-fund configuration atomically: account-type inclusion list,
     * target months, and amber-floor months. {@link Account.AccountType#BANK_SAVINGS} must be in
     * the types list. {@code targetMonths} must be in {@code [2, 24]}; {@code amberFloorMonths}
     * must be in {@code [1, targetMonths - 1]}.
     */
    @Transactional
    public void updateEmergencyFundConfig(
            UUID userId, List<Account.AccountType> types, int targetMonths, int amberFloorMonths) {
        String username = currentUsername();

        if (types == null || !types.contains(Account.AccountType.BANK_SAVINGS)) {
            auditService.failure(
                    USER_SETTINGS_EMERGENCY_FUND_UPDATED, userId, username, "BANK_SAVINGS missing");
            throw new BusinessRuleException(
                    "BANK_SAVINGS must be included", "EMERGENCY_FUND_BANK_SAVINGS_REQUIRED");
        }
        if (targetMonths < MIN_TARGET || targetMonths > MAX_TARGET) {
            auditService.failure(
                    USER_SETTINGS_EMERGENCY_FUND_UPDATED,
                    userId,
                    username,
                    "target out of range: " + targetMonths);
            throw new BusinessRuleException(
                    "Target months must be between " + MIN_TARGET + " and " + MAX_TARGET,
                    "EMERGENCY_FUND_TARGET_OUT_OF_RANGE");
        }
        if (amberFloorMonths < MIN_AMBER_FLOOR || amberFloorMonths >= targetMonths) {
            auditService.failure(
                    USER_SETTINGS_EMERGENCY_FUND_UPDATED,
                    userId,
                    username,
                    "amber floor invalid: " + amberFloorMonths + " (target=" + targetMonths + ")");
            throw new BusinessRuleException(
                    "Amber floor must be at least " + MIN_AMBER_FLOOR + " and less than target",
                    "EMERGENCY_FUND_AMBER_FLOOR_INVALID");
        }

        UserSettings settings =
                repository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Settings not found"));
        settings.setEmergencyFundIncludeTypes(types.stream().map(Enum::name).toList());
        settings.setEmergencyFundTargetMonths((short) targetMonths);
        settings.setEmergencyFundAmberFloorMonths((short) amberFloorMonths);
        repository.save(settings);

        auditService.success(
                USER_SETTINGS_EMERGENCY_FUND_UPDATED,
                userId,
                username,
                "types=" + types + " target=" + targetMonths + " amberFloor=" + amberFloorMonths);
    }

    /**
     * Updates the per-user rebalance drift tolerance threshold. The controller-level Bean
     * Validation handles the obvious cases; this service-level guard catches direct internal calls
     * and emits the {@code USER_SETTINGS_REBALANCE_THRESHOLD_UPDATED} audit on success or failure.
     */
    @Transactional
    public BigDecimal updateRebalanceDriftThreshold(UUID userId, BigDecimal threshold) {
        String username = currentUsername();
        if (threshold == null
                || threshold.compareTo(MIN_REBALANCE_THRESHOLD) < 0
                || threshold.compareTo(MAX_REBALANCE_THRESHOLD) > 0) {
            auditService.failure(
                    USER_SETTINGS_REBALANCE_THRESHOLD_UPDATED,
                    userId,
                    username,
                    "out of range: " + threshold);
            throw new BusinessRuleException(
                    "Rebalance drift threshold must be between "
                            + MIN_REBALANCE_THRESHOLD
                            + " and "
                            + MAX_REBALANCE_THRESHOLD,
                    "REBALANCE_THRESHOLD_OUT_OF_RANGE");
        }
        UserSettings settings =
                repository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Settings not found"));
        settings.setRebalanceDriftThresholdPercent(threshold);
        repository.save(settings);
        auditService.success(
                USER_SETTINGS_REBALANCE_THRESHOLD_UPDATED,
                userId,
                username,
                "threshold=" + threshold);
        return threshold;
    }

    private SettingsResponse toResponse(UserSettings s) {
        return new SettingsResponse(
                s.getCurrency(),
                s.getLanguage(),
                s.getTheme(),
                s.getTimezone(),
                s.isOnboardingCompleted());
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
