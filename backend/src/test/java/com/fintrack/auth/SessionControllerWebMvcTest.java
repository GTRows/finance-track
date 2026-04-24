package com.fintrack.auth;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.RefreshToken;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SessionControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean RefreshTokenService refreshTokenService;
    @MockBean RefreshTokenRepository refreshTokenRepository;
    @MockBean AuditService auditService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private RefreshToken sessionRow(UUID id) {
        return RefreshToken.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .token("t")
                .userAgent("Mozilla")
                .ipAddress("1.2.3.4")
                .expiresAt(Instant.now().plusSeconds(60))
                .lastUsedAt(Instant.now())
                .build();
    }

    @Test
    void listReturnsActiveSessions() throws Exception {
        stubAuthenticatedUser();
        UUID sid = UUID.randomUUID();
        when(refreshTokenService.listActive(eq(userId))).thenReturn(List.of(sessionRow(sid)));

        mockMvc.perform(
                        post("/api/v1/auth/sessions/list")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sid.toString()))
                .andExpect(jsonPath("$[0].current").value(false));
    }

    @Test
    void listFlagsCurrentSessionByRefreshToken() throws Exception {
        stubAuthenticatedUser();
        UUID sid = UUID.randomUUID();
        RefreshToken current = sessionRow(sid);
        when(refreshTokenRepository.findByToken("my-token")).thenReturn(Optional.of(current));
        when(refreshTokenService.listActive(eq(userId))).thenReturn(List.of(current));

        mockMvc.perform(
                        post("/api/v1/auth/sessions/list")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"my-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void revokeReturns204WhenSucceeded() throws Exception {
        stubAuthenticatedUser();
        UUID sid = UUID.randomUUID();
        when(refreshTokenService.revokeSession(eq(userId), eq(sid))).thenReturn(true);

        mockMvc.perform(delete("/api/v1/auth/sessions/" + sid).with(csrf()))
                .andExpect(status().isNoContent());

        verify(refreshTokenService).revokeSession(eq(userId), eq(sid));
    }

    @Test
    void revoke404WhenSessionMissing() throws Exception {
        stubAuthenticatedUser();
        UUID sid = UUID.randomUUID();
        when(refreshTokenService.revokeSession(eq(userId), eq(sid))).thenReturn(false);

        mockMvc.perform(delete("/api/v1/auth/sessions/" + sid).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void revokeOthers404WhenCurrentTokenMissing() throws Exception {
        stubAuthenticatedUser();
        when(refreshTokenRepository.findByToken("bad")).thenReturn(Optional.empty());

        mockMvc.perform(
                        post("/api/v1/auth/sessions/revoke-others")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void revokeOthersReturnsCount() throws Exception {
        stubAuthenticatedUser();
        UUID keepId = UUID.randomUUID();
        when(refreshTokenRepository.findByToken("t")).thenReturn(Optional.of(sessionRow(keepId)));
        when(refreshTokenService.revokeOthers(eq(userId), eq(keepId))).thenReturn(3);

        mockMvc.perform(
                        post("/api/v1/auth/sessions/revoke-others")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"t\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(3));
    }
}
