package com.fintrack.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsServiceRebalanceThresholdTest {

    @Mock UserSettingsRepository repository;
    @Mock AuditService auditService;

    @InjectMocks SettingsService service;

    @Test
    void updateRebalanceDriftThreshold_happyPath() {
        UUID userId = UUID.randomUUID();
        UserSettings settings = UserSettings.builder().userId(userId).build();
        when(repository.findById(userId)).thenReturn(Optional.of(settings));
        when(repository.save(any(UserSettings.class))).thenReturn(settings);

        BigDecimal result = service.updateRebalanceDriftThreshold(userId, new BigDecimal("2.50"));

        assertThat(result).isEqualByComparingTo("2.50");
        assertThat(settings.getRebalanceDriftThresholdPercent()).isEqualByComparingTo("2.50");
    }

    @Test
    void updateRebalanceDriftThreshold_throwsBelowMin() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(
                        () -> service.updateRebalanceDriftThreshold(userId, new BigDecimal("0.05")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("REBALANCE_THRESHOLD_OUT_OF_RANGE");
    }

    @Test
    void updateRebalanceDriftThreshold_throwsAboveMax() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.updateRebalanceDriftThreshold(
                                        userId, new BigDecimal("11.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("REBALANCE_THRESHOLD_OUT_OF_RANGE");
    }

    @Test
    void updateRebalanceDriftThreshold_emitsAudit() {
        UUID userId = UUID.randomUUID();
        UserSettings settings = UserSettings.builder().userId(userId).build();
        when(repository.findById(userId)).thenReturn(Optional.of(settings));
        when(repository.save(any(UserSettings.class))).thenReturn(settings);

        service.updateRebalanceDriftThreshold(userId, new BigDecimal("1.50"));

        verify(auditService)
                .success(
                        eq(AuditAction.USER_SETTINGS_REBALANCE_THRESHOLD_UPDATED),
                        eq(userId),
                        any(),
                        anyString());
    }
}
