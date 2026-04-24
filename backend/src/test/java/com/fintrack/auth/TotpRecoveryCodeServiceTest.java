package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.TotpRecoveryCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TotpRecoveryCodeServiceTest {

    @Mock TotpRecoveryCodeRepository repository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks TotpRecoveryCodeService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void regenerateDropsExistingAndReturnsTenPlaintextCodes() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "h:" + inv.getArgument(0));

        List<String> codes = service.regenerate(userId);

        verify(repository).deleteByUserId(userId);
        verify(repository, times(10)).save(any(TotpRecoveryCode.class));
        assertThat(codes).hasSize(10);
        assertThat(codes).allMatch(c -> c.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertThat(codes.stream().distinct().count()).isEqualTo(10);
    }

    @Test
    void regenerateSavedCodesContainExpectedHashAndUserId() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "h:" + inv.getArgument(0));

        service.regenerate(userId);

        ArgumentCaptor<TotpRecoveryCode> captor = ArgumentCaptor.forClass(TotpRecoveryCode.class);
        verify(repository, times(10)).save(captor.capture());
        for (TotpRecoveryCode saved : captor.getAllValues()) {
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getCodeHash()).startsWith("h:");
            assertThat(saved.getConsumedAt()).isNull();
        }
    }

    @Test
    void redeemReturnsFalseForBlankInput() {
        assertThat(service.redeem(userId, null)).isFalse();
        assertThat(service.redeem(userId, "   ")).isFalse();
        verify(repository, never()).findActiveByUserId(any());
    }

    @Test
    void redeemReturnsFalseForWrongLength() {
        assertThat(service.redeem(userId, "XX-YY")).isFalse();
        verify(repository, never()).findActiveByUserId(any());
    }

    @Test
    void redeemNormalisesUnhyphenatedInputToDashedAndMatches() {
        TotpRecoveryCode code =
                TotpRecoveryCode.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .codeHash("h")
                        .build();
        when(repository.findActiveByUserId(userId)).thenReturn(new ArrayList<>(List.of(code)));
        when(passwordEncoder.matches("ABCDE-FGHJK", "h")).thenReturn(true);

        assertThat(service.redeem(userId, "abcdefghjk")).isTrue();
        assertThat(code.getConsumedAt()).isNotNull();
        verify(repository).save(code);
    }

    @Test
    void redeemReturnsFalseWhenNoActiveCodeMatches() {
        TotpRecoveryCode code =
                TotpRecoveryCode.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .codeHash("h")
                        .build();
        when(repository.findActiveByUserId(userId)).thenReturn(List.of(code));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThat(service.redeem(userId, "AAAAA-BBBBB")).isFalse();
        assertThat(code.getConsumedAt()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void redeemStopsAtFirstMatch() {
        TotpRecoveryCode c1 =
                TotpRecoveryCode.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .codeHash("h1")
                        .build();
        TotpRecoveryCode c2 =
                TotpRecoveryCode.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .codeHash("h2")
                        .build();
        when(repository.findActiveByUserId(userId)).thenReturn(List.of(c1, c2));
        when(passwordEncoder.matches("AAAAA-BBBBB", "h1")).thenReturn(false);
        when(passwordEncoder.matches("AAAAA-BBBBB", "h2")).thenReturn(true);

        assertThat(service.redeem(userId, "AAAAA-BBBBB")).isTrue();
        assertThat(c1.getConsumedAt()).isNull();
        assertThat(c2.getConsumedAt()).isNotNull();
    }

    @Test
    void remainingDelegatesToRepository() {
        when(repository.countByUserIdAndConsumedAtIsNull(userId)).thenReturn(4L);

        assertThat(service.remaining(userId)).isEqualTo(4L);
    }

    @Test
    void invalidateAllDelegatesToRepository() {
        service.invalidateAll(userId);

        verify(repository).deleteByUserId(userId);
    }

    @Test
    void redeemPreservesConsumedAtOnAlreadyRedeemedCodeByOnlyLookingAtActiveSet() {
        when(repository.findActiveByUserId(userId)).thenReturn(List.of());

        assertThat(service.redeem(userId, "AAAAA-BBBBB")).isFalse();
        verify(repository).findActiveByUserId(userId);
    }
}
