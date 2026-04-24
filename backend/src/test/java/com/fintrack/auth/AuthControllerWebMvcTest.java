package com.fintrack.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.auth.dto.AuthResponse;
import com.fintrack.auth.dto.LoginRequest;
import com.fintrack.auth.dto.RecoveryCodesResponse;
import com.fintrack.auth.dto.RefreshRequest;
import com.fintrack.auth.dto.RegisterRequest;
import com.fintrack.auth.dto.TotpDisableRequest;
import com.fintrack.auth.dto.TotpEnableRequest;
import com.fintrack.auth.dto.TotpEnableResponse;
import com.fintrack.auth.dto.TotpRecoveryVerifyRequest;
import com.fintrack.auth.dto.TotpVerifyRequest;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private void stubAuthenticatedUser() {
        com.fintrack.common.entity.User user =
                com.fintrack.common.entity.User.builder()
                        .id(UUID.randomUUID())
                        .username("ali")
                        .email("ali@example.com")
                        .password("pw")
                        .role(com.fintrack.common.entity.User.Role.USER)
                        .build();
        FinTrackUserDetails principal = new FinTrackUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextImpl ctx = new SecurityContextImpl(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerReturns201WithTokenPair() throws Exception {
        AuthResponse res = AuthResponse.of("access", "refresh", 900, null);
        when(authService.register(any(RegisterRequest.class))).thenReturn(res);

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"ali\",\"email\":\"ali@example.com\",\"password\":\"longenough\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.requiresTotp").value(false));
    }

    @Test
    void registerRejectsBlankUsername() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"\",\"email\":\"ali@example.com\",\"password\":\"longenough\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"ali\",\"email\":\"not-an-email\",\"password\":\"longenough\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"ali\",\"email\":\"ali@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void loginReturnsTokenPair() throws Exception {
        AuthResponse res = AuthResponse.of("a", "r", 900, null);
        when(authService.login(any(LoginRequest.class))).thenReturn(res);

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"ali\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a"));
    }

    @Test
    void loginRejectsBlankUsername() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"\",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void loginPropagatesRateLimitException() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new LoginRateLimitException("Too many attempts"));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"ali\",\"password\":\"pw\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void loginChallengeOmitsTokens() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(AuthResponse.challenge("cha"));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"ali\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresTotp").value(true))
                .andExpect(jsonPath("$.totpChallengeToken").value("cha"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void verifyTotpCompletesLogin() throws Exception {
        when(authService.verifyTotp(any(TotpVerifyRequest.class)))
                .thenReturn(AuthResponse.of("a", "r", 900, null));

        mockMvc.perform(
                        post("/api/v1/auth/2fa/verify")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"challengeToken\":\"c\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a"));
    }

    @Test
    void verifyTotpRejectsInvalidCode() throws Exception {
        when(authService.verifyTotp(any(TotpVerifyRequest.class)))
                .thenThrow(new BusinessRuleException("Invalid verification code", "TOTP_INVALID"));

        mockMvc.perform(
                        post("/api/v1/auth/2fa/verify")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"challengeToken\":\"c\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));
    }

    @Test
    void totpEnableReturnsRecoveryCodes() throws Exception {
        stubAuthenticatedUser();
        when(authService.totpEnable(any(), any(TotpEnableRequest.class)))
                .thenReturn(new TotpEnableResponse(List.of("AAAAA-BBBBB", "CCCCC-DDDDD")));

        mockMvc.perform(
                        post("/api/v1/auth/2fa/enable")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes[0]").value("AAAAA-BBBBB"))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(2));
    }

    @Test
    void totpDisableReturns204() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/auth/2fa/disable")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"pw\"}"))
                .andExpect(status().isNoContent());

        verify(authService).totpDisable(any(), any(TotpDisableRequest.class));
    }

    @Test
    void regenerateRecoveryCodesRequiresPassword() throws Exception {
        stubAuthenticatedUser();
        when(authService.regenerateRecoveryCodes(any(), any(TotpDisableRequest.class)))
                .thenReturn(new RecoveryCodesResponse(List.of("NEW11-NEW22")));

        mockMvc.perform(
                        post("/api/v1/auth/2fa/recovery-codes/regenerate")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes[0]").value("NEW11-NEW22"));
    }

    @Test
    void verifyRecoveryCodeCompletesLogin() throws Exception {
        when(authService.verifyRecoveryCode(any(TotpRecoveryVerifyRequest.class)))
                .thenReturn(AuthResponse.of("a", "r", 900, null));

        mockMvc.perform(
                        post("/api/v1/auth/2fa/recovery/verify")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"challengeToken\":\"c\",\"recoveryCode\":\"AAAAA-BBBBB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("r"));
    }

    @Test
    void verifyRecoveryCodeRejectsInvalid() throws Exception {
        when(authService.verifyRecoveryCode(any(TotpRecoveryVerifyRequest.class)))
                .thenThrow(
                        new BusinessRuleException(
                                "Invalid recovery code", "RECOVERY_CODE_INVALID"));

        mockMvc.perform(
                        post("/api/v1/auth/2fa/recovery/verify")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"challengeToken\":\"c\",\"recoveryCode\":\"ZZZZZ-YYYYY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECOVERY_CODE_INVALID"));
    }

    @Test
    void refreshEndpointPassesThrough() throws Exception {
        when(authService.refresh(any(RefreshRequest.class)))
                .thenReturn(AuthResponse.of("a2", "r2", 900, null));

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"r\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a2"));
    }

    @Test
    void refreshRejectsBlankToken() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void logoutReturns204() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"r\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout("r");
    }
}
