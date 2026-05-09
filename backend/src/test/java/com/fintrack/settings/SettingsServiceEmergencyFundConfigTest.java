package com.fintrack.settings;

import static com.fintrack.audit.AuditAction.USER_SETTINGS_EMERGENCY_FUND_UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.common.exception.BusinessRuleException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsServiceEmergencyFundConfigTest {

    @Mock UserSettingsRepository repository;
    @Mock AuditService auditService;

    @InjectMocks SettingsService service;

    private final UUID userId = UUID.randomUUID();

    private UserSettings existing() {
        return UserSettings.builder()
                .userId(userId)
                .emergencyFundTargetMonths((short) 6)
                .emergencyFundAmberFloorMonths((short) 3)
                .build();
    }

    @Test
    void updateConfig_persistsAllThreeFields_andEmitsAuditSuccess() {
        UserSettings settings = existing();
        when(repository.findById(userId)).thenReturn(Optional.of(settings));
        when(repository.save(settings)).thenReturn(settings);

        service.updateEmergencyFundConfig(
                userId, List.of(Account.AccountType.BANK_SAVINGS, Account.AccountType.CASH), 9, 4);

        assertThat(settings.getEmergencyFundIncludeTypes()).containsExactly("BANK_SAVINGS", "CASH");
        assertThat(settings.getEmergencyFundTargetMonths()).isEqualTo((short) 9);
        assertThat(settings.getEmergencyFundAmberFloorMonths()).isEqualTo((short) 4);
        verify(repository).save(settings);
        verify(auditService)
                .success(
                        eq(USER_SETTINGS_EMERGENCY_FUND_UPDATED),
                        eq(userId),
                        any(),
                        contains("target=9 amberFloor=4"));
    }

    @Test
    void updateConfig_throwsWhenSavingsMissing() {
        assertThatThrownBy(
                        () ->
                                service.updateEmergencyFundConfig(
                                        userId, List.of(Account.AccountType.CASH), 6, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "EMERGENCY_FUND_BANK_SAVINGS_REQUIRED");

        verify(auditService)
                .failure(
                        eq(USER_SETTINGS_EMERGENCY_FUND_UPDATED),
                        eq(userId),
                        any(),
                        contains("BANK_SAVINGS missing"));
        verify(repository, never()).save(any());
    }

    @Test
    void updateConfig_throwsWhenTargetTooLow() {
        assertThatThrownBy(
                        () ->
                                service.updateEmergencyFundConfig(
                                        userId, List.of(Account.AccountType.BANK_SAVINGS), 1, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "EMERGENCY_FUND_TARGET_OUT_OF_RANGE");

        verify(auditService)
                .failure(
                        eq(USER_SETTINGS_EMERGENCY_FUND_UPDATED),
                        eq(userId),
                        any(),
                        contains("target out of range"));
        verify(repository, never()).save(any());
    }

    @Test
    void updateConfig_throwsWhenTargetTooHigh() {
        assertThatThrownBy(
                        () ->
                                service.updateEmergencyFundConfig(
                                        userId, List.of(Account.AccountType.BANK_SAVINGS), 25, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "EMERGENCY_FUND_TARGET_OUT_OF_RANGE");

        verify(repository, never()).save(any());
    }

    @Test
    void updateConfig_throwsWhenAmberFloorEqualsTarget() {
        assertThatThrownBy(
                        () ->
                                service.updateEmergencyFundConfig(
                                        userId, List.of(Account.AccountType.BANK_SAVINGS), 6, 6))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "EMERGENCY_FUND_AMBER_FLOOR_INVALID");

        verify(auditService)
                .failure(
                        eq(USER_SETTINGS_EMERGENCY_FUND_UPDATED),
                        eq(userId),
                        any(),
                        contains("amber floor invalid"));
        verify(repository, never()).save(any());
    }

    @Test
    void updateConfig_throwsWhenAmberFloorBelowOne() {
        assertThatThrownBy(
                        () ->
                                service.updateEmergencyFundConfig(
                                        userId, List.of(Account.AccountType.BANK_SAVINGS), 6, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "EMERGENCY_FUND_AMBER_FLOOR_INVALID");

        verify(repository, never()).save(any());
    }
}
