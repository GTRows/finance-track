package com.fintrack.auth;

import com.fintrack.audit.AuditService;
import com.fintrack.auth.dto.TotpDisableRequest;
import com.fintrack.auth.dto.TotpEnableRequest;
import com.fintrack.auth.dto.TotpSetupResponse;
import com.fintrack.auth.dto.TotpStatusResponse;
import com.fintrack.auth.dto.TotpVerifyRequest;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.settings.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTotpTest {

    @Mock UserRepository userRepository;
    @Mock UserSettingsRepository userSettingsRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtUtil jwtUtil;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock TotpService totpService;
    @Mock AuditService auditService;

    @InjectMocks AuthService authService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .username("ali")
                .email("ali@example.com")
                .password("bcrypt-hash")
                .role(User.Role.USER)
                .totpEnabled(false)
                .build();
    }

    @Test
    void setupGeneratesAndStoresSecret() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpService.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.buildOtpauthUrl("JBSWY3DPEHPK3PXP", "ali@example.com"))
                .thenReturn("otpauth://totp/...");

        TotpSetupResponse response = authService.totpSetup(userId);

        assertEquals("JBSWY3DPEHPK3PXP", response.secret());
        assertEquals("JBSWY3DPEHPK3PXP", user.getTotpSecret());
        assertFalse(user.isTotpEnabled());
        verify(userRepository).save(user);
        verify(auditService).success("TOTP_SETUP", userId, "ali");
    }

    @Test
    void setupRejectsWhenAlreadyEnabled() {
        user.setTotpEnabled(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.totpSetup(userId));
        assertEquals("TOTP_ALREADY_ENABLED", ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void enableActivatesAfterValidCode() {
        user.setTotpSecret("secret");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpService.verify("secret", "123456")).thenReturn(true);

        authService.totpEnable(userId, new TotpEnableRequest("123456"));

        assertTrue(user.isTotpEnabled());
        verify(userRepository).save(user);
        verify(auditService).success("TOTP_ENABLE", userId, "ali");
    }

    @Test
    void enableRejectsWrongCodeAndAudits() {
        user.setTotpSecret("secret");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpService.verify("secret", "999999")).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.totpEnable(userId, new TotpEnableRequest("999999")));
        assertEquals("TOTP_INVALID", ex.getCode());
        assertFalse(user.isTotpEnabled());
        verify(auditService).failure("TOTP_ENABLE", userId, "ali", "invalid code");
    }

    @Test
    void enableFailsWhenNoSecretProvisioned() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.totpEnable(userId, new TotpEnableRequest("123456")));
        assertEquals("TOTP_NOT_STARTED", ex.getCode());
    }

    @Test
    void disableWipesSecretAfterPasswordCheck() {
        user.setTotpSecret("secret");
        user.setTotpEnabled(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "bcrypt-hash")).thenReturn(true);

        authService.totpDisable(userId, new TotpDisableRequest("pw"));

        assertFalse(user.isTotpEnabled());
        assertNull(user.getTotpSecret());
        verify(auditService).success("TOTP_DISABLE", userId, "ali");
    }

    @Test
    void disableRejectsWrongPassword() {
        user.setTotpSecret("secret");
        user.setTotpEnabled(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.totpDisable(userId, new TotpDisableRequest("wrong")));
        assertEquals("PASSWORD_INVALID", ex.getCode());
        assertTrue(user.isTotpEnabled());
        verify(auditService).failure("TOTP_DISABLE", userId, "ali", "wrong password");
    }

    @Test
    void statusReflectsFlag() {
        user.setTotpEnabled(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        TotpStatusResponse status = authService.totpStatus(userId);
        assertTrue(status.enabled());
    }

    @Test
    void verifyTotpRejectsInvalidChallengeToken() {
        when(jwtUtil.validateTotpChallenge("bad-token")).thenReturn(null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.verifyTotp(new TotpVerifyRequest("bad-token", "123456")));
        assertEquals("TOTP_CHALLENGE_INVALID", ex.getCode());
        verify(auditService).failure("TOTP_VERIFY", (String) null, "invalid challenge token");
    }
}
