package com.fintrack.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditService;
import com.fintrack.auth.dto.PasswordChangeRequest;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.settings.UserSettingsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordTest {

    @Mock UserRepository userRepository;
    @Mock UserSettingsRepository userSettingsRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtUtil jwtUtil;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock TotpService totpService;
    @Mock AuditService auditService;
    @Mock LoginRateLimiter loginRateLimiter;

    @InjectMocks AuthService authService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user =
                User.builder()
                        .id(userId)
                        .username("ali")
                        .email("ali@example.com")
                        .password("bcrypt-current")
                        .role(User.Role.USER)
                        .build();
    }

    @Test
    void changePasswordUpdatesHashAndRevokesTokens() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pw", "bcrypt-current")).thenReturn(true);
        when(passwordEncoder.matches("new-strong-pw", "bcrypt-current")).thenReturn(false);
        when(passwordEncoder.encode("new-strong-pw")).thenReturn("bcrypt-new");

        authService.changePassword(
                userId, new PasswordChangeRequest("current-pw", "new-strong-pw"));

        assertEquals("bcrypt-new", user.getPassword());
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(userId);
        verify(auditService).success("PASSWORD_CHANGE", userId, "ali");
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "bcrypt-current")).thenReturn(false);

        BusinessRuleException ex =
                assertThrows(
                        BusinessRuleException.class,
                        () ->
                                authService.changePassword(
                                        userId,
                                        new PasswordChangeRequest("wrong", "new-strong-pw")));

        assertEquals("PASSWORD_INVALID", ex.getCode());
        verify(userRepository, never()).save(user);
        verify(refreshTokenService, never()).revokeAllForUser(userId);
        verify(auditService).failure("PASSWORD_CHANGE", userId, "ali", "wrong current password");
    }

    @Test
    void changePasswordRejectsSamePassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pw", "bcrypt-current")).thenReturn(true);

        BusinessRuleException ex =
                assertThrows(
                        BusinessRuleException.class,
                        () ->
                                authService.changePassword(
                                        userId,
                                        new PasswordChangeRequest("current-pw", "current-pw")));

        assertEquals("PASSWORD_UNCHANGED", ex.getCode());
        verify(userRepository, never()).save(user);
        verify(refreshTokenService, never()).revokeAllForUser(userId);
    }
}
