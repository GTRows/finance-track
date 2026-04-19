package com.fintrack.auth;

import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.PasswordReset;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.notification.MailProperties;
import com.fintrack.notification.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock PasswordResetRepository repository;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock MailService mailService;
    @Mock MailProperties mailProperties;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;

    @InjectMocks PasswordResetService service;

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("ali")
                .email("ali@example.com")
                .password("bcrypt-current")
                .build();
    }

    @Test
    void requestResetIsSilentForUnknownEmail() {
        when(userRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@x.com");

        verify(repository, never()).save(any());
        verify(mailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void requestResetConsumesOutstandingAndSendsMailWithNewToken() {
        User u = user();
        when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        when(mailProperties.getBaseUrl()).thenReturn("http://app");

        service.requestReset(u.getEmail());

        verify(repository).consumeOutstandingForUser(eq(u.getId()), any(Instant.class));
        ArgumentCaptor<PasswordReset> captor = ArgumentCaptor.forClass(PasswordReset.class);
        verify(repository).save(captor.capture());
        PasswordReset saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(u.getId());
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(1800));
        verify(mailService).sendHtml(eq(u.getEmail()), anyString(), anyString());
    }

    @Test
    void confirmResetThrowsWhenTokenMissing() {
        when(repository.findByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReset("bad", "new-pw"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void confirmResetThrowsWhenAlreadyUsed() {
        PasswordReset entry = PasswordReset.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).token("t")
                .expiresAt(Instant.now().plusSeconds(60))
                .consumedAt(Instant.now().minusSeconds(5)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.confirmReset("t", "new-pw"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void confirmResetThrowsWhenExpired() {
        PasswordReset entry = PasswordReset.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).token("t")
                .expiresAt(Instant.now().minusSeconds(60)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.confirmReset("t", "new-pw"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void confirmResetThrowsWhenUserMissing() {
        PasswordReset entry = PasswordReset.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).token("t")
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));
        when(userRepository.findById(entry.getUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReset("t", "new-pw"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirmResetRejectsReuseOfSamePassword() {
        User u = user();
        PasswordReset entry = PasswordReset.builder()
                .id(UUID.randomUUID()).userId(u.getId()).token("t")
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("new-pw", "bcrypt-current")).thenReturn(true);

        assertThatThrownBy(() -> service.confirmReset("t", "new-pw"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("differ");

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void confirmResetSetsNewPasswordAndRevokesSessions() {
        User u = user();
        PasswordReset entry = PasswordReset.builder()
                .id(UUID.randomUUID()).userId(u.getId()).token("t")
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("new-pw", "bcrypt-current")).thenReturn(false);
        when(passwordEncoder.encode("new-pw")).thenReturn("bcrypt-new");

        service.confirmReset("t", "new-pw");

        assertThat(entry.getConsumedAt()).isNotNull();
        assertThat(u.getPassword()).isEqualTo("bcrypt-new");
        verify(repository).save(entry);
        verify(userRepository).save(u);
        verify(refreshTokenService).revokeAllForUser(u.getId());
    }
}
