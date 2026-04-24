package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.RefreshToken;
import com.fintrack.common.exception.BusinessRuleException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtUtil jwtUtil;

    @InjectMocks RefreshTokenService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createPersistsEntityWithExpiryAndMetadata() {
        when(jwtUtil.generateRefreshToken(userId.toString())).thenReturn("raw-token");
        when(jwtUtil.getRefreshExpiryMs()).thenReturn(60_000L);

        String raw = service.createRefreshToken(userId, "Mozilla/5.0", "1.2.3.4");

        assertThat(raw).isEqualTo("raw-token");
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getToken()).isEqualTo("raw-token");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getIpAddress()).isEqualTo("1.2.3.4");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusMillis(50_000L));
        assertThat(saved.getLastUsedAt()).isNotNull();
    }

    @Test
    void createTruncatesLongUserAgentAndIp() {
        when(jwtUtil.generateRefreshToken(any())).thenReturn("t");
        when(jwtUtil.getRefreshExpiryMs()).thenReturn(1_000L);

        String bigUa = "a".repeat(800);
        String bigIp = "0123456789012345678901234567890123456789012345678"; // 49 chars

        service.createRefreshToken(userId, bigUa, bigIp);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(512);
        assertThat(captor.getValue().getIpAddress()).hasSize(45);
    }

    @Test
    void validateReturnsEntityWhenPresentAndNotExpired() {
        RefreshToken entity =
                RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .token("t")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        when(refreshTokenRepository.findByToken("t")).thenReturn(Optional.of(entity));

        assertThat(service.validate("t")).isSameAs(entity);
    }

    @Test
    void validateThrowsWhenTokenMissing() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate("missing"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void validateThrowsWhenExpired() {
        RefreshToken entity =
                RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .token("t")
                        .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                        .build();
        when(refreshTokenRepository.findByToken("t")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.validate("t"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rotateDeletesOldAndCreatesNewCarryingMetadata() {
        RefreshToken old =
                RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .token("old")
                        .userAgent("ua")
                        .ipAddress("ip")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        when(refreshTokenRepository.findByToken("old")).thenReturn(Optional.of(old));
        when(jwtUtil.generateRefreshToken(userId.toString())).thenReturn("new-token");
        when(jwtUtil.getRefreshExpiryMs()).thenReturn(1_000L);

        String res = service.rotate("old", userId, null, null);

        assertThat(res).isEqualTo("new-token");
        verify(refreshTokenRepository).deleteByToken("old");
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).isEqualTo("ua");
        assertThat(captor.getValue().getIpAddress()).isEqualTo("ip");
    }

    @Test
    void rotatePrefersNewlyProvidedMetadataOverStoredValues() {
        RefreshToken old =
                RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .token("old")
                        .userAgent("old-ua")
                        .ipAddress("old-ip")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        when(refreshTokenRepository.findByToken("old")).thenReturn(Optional.of(old));
        when(jwtUtil.generateRefreshToken(userId.toString())).thenReturn("new-token");
        when(jwtUtil.getRefreshExpiryMs()).thenReturn(1_000L);

        service.rotate("old", userId, "new-ua", "new-ip");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).isEqualTo("new-ua");
        assertThat(captor.getValue().getIpAddress()).isEqualTo("new-ip");
    }

    @Test
    void revokeSessionReturnsFalseWhenNotOwned() {
        UUID sessionId = UUID.randomUUID();
        when(refreshTokenRepository.findByIdAndUserId(sessionId, userId))
                .thenReturn(Optional.empty());

        assertThat(service.revokeSession(userId, sessionId)).isFalse();
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void revokeSessionDeletesWhenOwned() {
        UUID sessionId = UUID.randomUUID();
        RefreshToken rt =
                RefreshToken.builder()
                        .id(sessionId)
                        .userId(userId)
                        .token("t")
                        .expiresAt(Instant.now().plusSeconds(10))
                        .build();
        when(refreshTokenRepository.findByIdAndUserId(sessionId, userId))
                .thenReturn(Optional.of(rt));

        assertThat(service.revokeSession(userId, sessionId)).isTrue();
        verify(refreshTokenRepository).delete(rt);
    }

    @Test
    void listActiveDelegatesToRepoWithCurrentInstant() {
        when(refreshTokenRepository.findByUserIdAndExpiresAtAfterOrderByLastUsedAtDesc(
                        any(), any()))
                .thenReturn(List.of());

        assertThat(service.listActive(userId)).isEmpty();
    }

    @Test
    void revokeOthersPassesThroughCount() {
        UUID keep = UUID.randomUUID();
        when(refreshTokenRepository.deleteByUserIdExcept(userId, keep)).thenReturn(3);

        assertThat(service.revokeOthers(userId, keep)).isEqualTo(3);
    }

    @Test
    void peekUserIdReturnsEmptyWhenTokenMissing() {
        when(refreshTokenRepository.findByToken("x")).thenReturn(Optional.empty());

        assertThat(service.peekUserId("x")).isEmpty();
    }

    @Test
    void peekUserIdReturnsUserIdWhenTokenPresent() {
        RefreshToken rt =
                RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .token("x")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        when(refreshTokenRepository.findByToken("x")).thenReturn(Optional.of(rt));

        assertThat(service.peekUserId("x")).contains(userId);
    }

    @Test
    void revokeAllForUserDelegatesToRepo() {
        service.revokeAllForUser(userId);
        verify(refreshTokenRepository).deleteByUserId(userId);
    }

    @Test
    void cleanupExpiredReturnsDeletedCount() {
        when(refreshTokenRepository.deleteExpired(any())).thenReturn(5);

        assertThat(service.cleanupExpired()).isEqualTo(5);
    }
}
