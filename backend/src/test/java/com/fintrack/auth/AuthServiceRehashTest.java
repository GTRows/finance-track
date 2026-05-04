package com.fintrack.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.dto.AuthResponse;
import com.fintrack.auth.dto.LoginRequest;
import com.fintrack.common.entity.User;
import com.fintrack.settings.UserSettingsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceRehashTest {

    @Mock UserRepository userRepository;
    @Mock UserSettingsRepository userSettingsRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtUtil jwtUtil;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock TotpService totpService;
    @Mock TotpRecoveryCodeService recoveryCodeService;
    @Mock AuditService auditService;
    @Mock LoginRateLimiter loginRateLimiter;
    @Mock EmailVerificationService emailVerificationService;

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
                        .password("{bcrypt}$2a$12$hashedvalue")
                        .role(User.Role.USER)
                        .build();
    }

    @Test
    void loginOnLegacyBcryptUserRehashesAndEmitsAudit() {
        when(userRepository.findByUsername("ali")).thenReturn(Optional.of(user));
        when(passwordEncoder.upgradeEncoding("{bcrypt}$2a$12$hashedvalue")).thenReturn(true);
        when(passwordEncoder.encode("pw")).thenReturn("{argon2}$argon2id$new-hash");
        when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString()))
                .thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(), any(), any()))
                .thenReturn("refresh-token");

        AuthResponse response = authService.login(new LoginRequest("ali", "pw"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(
                captor.getValue().getPassword().startsWith("{argon2}"),
                "Saved hash must start with {argon2}");
        verify(auditService).success(AuditAction.PASSWORD_REHASHED, userId, "ali", "argon2id");
        assertNotNull(response);
    }

    @Test
    void loginOnArgon2UserDoesNotRehash() {
        user.setPassword("{argon2}$argon2id$existing-hash");
        when(userRepository.findByUsername("ali")).thenReturn(Optional.of(user));
        when(passwordEncoder.upgradeEncoding("{argon2}$argon2id$existing-hash")).thenReturn(false);
        when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString()))
                .thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(), any(), any()))
                .thenReturn("refresh-token");

        authService.login(new LoginRequest("ali", "pw"));

        verify(userRepository, never()).save(any(User.class));
        verify(auditService, never())
                .success(
                        eq(AuditAction.PASSWORD_REHASHED),
                        any(UUID.class),
                        anyString(),
                        anyString());
    }

    @Test
    void failedLoginDoesNotTriggerRehash() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(
                        new org.springframework.security.authentication.BadCredentialsException(
                                "bad"));

        assertThrows(
                org.springframework.security.authentication.BadCredentialsException.class,
                () -> authService.login(new LoginRequest("ali", "wrong-pw")));

        verify(passwordEncoder, never()).upgradeEncoding(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}
