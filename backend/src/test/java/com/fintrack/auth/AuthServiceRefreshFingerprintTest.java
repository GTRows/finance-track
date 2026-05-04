package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.dto.AuthResponse;
import com.fintrack.auth.dto.RefreshRequest;
import com.fintrack.common.entity.RefreshToken;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.settings.UserSettingsRepository;
import java.time.Instant;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshFingerprintTest {

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
                        .password("{argon2}$argon2id$hash")
                        .role(User.Role.USER)
                        .build();
    }

    @Test
    void refreshPropagatesFingerprintArgumentsToValidate() {
        RefreshToken existing =
                RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .token("token")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        when(refreshTokenService.validate(eq("token"), any(), any())).thenReturn(existing);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenService.rotate(eq("token"), eq(userId), any(), any()))
                .thenReturn("new-refresh");
        when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString()))
                .thenReturn("access-token");

        AuthResponse response = authService.refresh(new RefreshRequest("token"));

        assertThat(response).isNotNull();
        ArgumentCaptor<String> uaCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenService).validate(eq("token"), uaCaptor.capture(), ipCaptor.capture());
        // Outside an HttpServletRequest the captors will be null; we are asserting call shape.
        assertThat(uaCaptor.getAllValues()).hasSize(1);
        assertThat(ipCaptor.getAllValues()).hasSize(1);
        verify(auditService).success(AuditAction.TOKEN_REFRESH, userId, "ali");
    }

    @Test
    void refreshPropagatesFingerprintMismatchAndDoesNotMintNewTokens() {
        when(refreshTokenService.validate(eq("token"), any(), any()))
                .thenThrow(
                        new BusinessRuleException(
                                "Session fingerprint mismatch", "REFRESH_FINGERPRINT_MISMATCH"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("token")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessRuleException) ex).getCode())
                                        .isEqualTo("REFRESH_FINGERPRINT_MISMATCH"));

        verify(refreshTokenService, never()).rotate(anyString(), any(), any(), any());
        verify(jwtUtil, never()).generateAccessToken(anyString(), anyString(), anyString());
        verify(auditService, never()).success(eq(AuditAction.TOKEN_REFRESH), any(), any());
    }
}
