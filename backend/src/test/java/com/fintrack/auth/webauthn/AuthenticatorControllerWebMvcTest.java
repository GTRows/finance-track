package com.fintrack.auth.webauthn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.audit.AuditService;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.auth.webauthn.dto.AuthenticatorResponse;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthenticatorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthenticatorControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean AuthenticatorRepository authenticatorRepository;
    @MockBean AuditService auditService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void listReturnsOnlyUsersAuthenticators() throws Exception {
        stubAuthenticatedUser();

        AuthenticatorResponse resp =
                new AuthenticatorResponse(
                        UUID.randomUUID(),
                        "Yubikey 5",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        "usb,nfc");

        when(authenticatorRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(
                        List.of(
                                com.fintrack.common.entity.Authenticator.builder()
                                        .id(resp.id())
                                        .userId(userId)
                                        .credentialId(new byte[] {1})
                                        .publicKeyCose(new byte[] {2})
                                        .signCount(0L)
                                        .name(resp.name())
                                        .transports(resp.transports())
                                        .createdAt(resp.createdAt())
                                        .build()));

        mockMvc.perform(get("/api/v1/auth/webauthn/authenticators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Yubikey 5"))
                .andExpect(jsonPath("$[0].transports").value("usb,nfc"));
    }

    @Test
    void deleteReturns204AndAudits() throws Exception {
        stubAuthenticatedUser();
        UUID credId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/auth/webauthn/authenticators/" + credId))
                .andExpect(status().isNoContent());

        verify(authenticatorRepository).deleteByIdAndUserId(eq(credId), eq(userId));
        verify(auditService)
                .success(
                        eq(com.fintrack.audit.AuditAction.WEBAUTHN_AUTHENTICATOR_REVOKED),
                        eq(userId),
                        any(),
                        any());
    }
}
