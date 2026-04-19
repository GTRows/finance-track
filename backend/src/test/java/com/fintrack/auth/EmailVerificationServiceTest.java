package com.fintrack.auth;

import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.EmailVerification;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.notification.MailProperties;
import com.fintrack.notification.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class EmailVerificationServiceTest {

    @Mock EmailVerificationRepository repository;
    @Mock UserRepository userRepository;
    @Mock MailService mailService;
    @Mock MailProperties mailProperties;
    @Mock AuditService auditService;

    @InjectMocks EmailVerificationService service;

    private User user(boolean verified) {
        return User.builder()
                .id(UUID.randomUUID())
                .username("ali")
                .email("ali@example.com")
                .password("pw")
                .emailVerified(verified)
                .build();
    }

    @Test
    void sendVerificationSkipsWhenAlreadyVerified() {
        User u = user(true);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

        service.sendVerification(u.getId());

        verify(repository, never()).save(any());
        verify(mailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void sendVerificationThrowsWhenUserMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendVerification(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sendVerificationConsumesOutstandingAndPersistsNewToken() {
        User u = user(false);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(mailProperties.getBaseUrl()).thenReturn("http://app");

        service.sendVerification(u.getId());

        verify(repository).consumeOutstandingForUser(eq(u.getId()), any(Instant.class));

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(repository).save(captor.capture());
        EmailVerification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(u.getId());
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(3600));

        verify(mailService).sendHtml(eq(u.getEmail()), anyString(), anyString());
        verify(auditService).success(any(), eq(u.getId()), eq(u.getUsername()));
    }

    @Test
    void confirmThrowsWhenTokenUnknown() {
        when(repository.findByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("bad"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void confirmThrowsWhenTokenAlreadyUsed() {
        EmailVerification entry = EmailVerification.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).token("t")
                .expiresAt(Instant.now().plusSeconds(60))
                .consumedAt(Instant.now().minusSeconds(10)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.confirm("t"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void confirmThrowsWhenExpired() {
        EmailVerification entry = EmailVerification.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).token("t")
                .expiresAt(Instant.now().minusSeconds(10)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.confirm("t"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void confirmMarksUserVerifiedAndConsumesToken() {
        User u = user(false);
        EmailVerification entry = EmailVerification.builder()
                .id(UUID.randomUUID()).userId(u.getId()).token("t")
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

        service.confirm("t");

        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.getEmailVerifiedAt()).isNotNull();
        assertThat(entry.getConsumedAt()).isNotNull();
        verify(repository).save(entry);
        verify(userRepository).save(u);
    }

    @Test
    void confirmDoesNotDoubleWriteUserWhenAlreadyVerified() {
        User u = user(true);
        EmailVerification entry = EmailVerification.builder()
                .id(UUID.randomUUID()).userId(u.getId()).token("t")
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findByToken("t")).thenReturn(Optional.of(entry));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

        service.confirm("t");

        assertThat(entry.getConsumedAt()).isNotNull();
        verify(userRepository, never()).save(any());
    }
}
