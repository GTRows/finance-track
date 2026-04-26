package com.fintrack.backup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BackupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BackupControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean BackupService backupService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private BackupPayload emptyPayload() {
        return new BackupPayload(
                new BackupPayload.BackupMeta(
                        1, Instant.parse("2026-04-01T10:00:00Z"), "u@example.com"),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void exportReturnsJson() throws Exception {
        stubAuthenticatedUser();
        when(backupService.export(eq(userId))).thenReturn(emptyPayload());

        mockMvc.perform(get("/api/v1/backup/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.version").value(1))
                .andExpect(jsonPath("$.meta.userEmail").value("u@example.com"));
    }

    @Test
    void restoreReturnsCounts() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/backup/import")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"meta\":{\"version\":1,\"exportedAt\":\"2026-04-01T10:00:00Z\",\"userEmail\":\"u@example.com\"},"
                                            + "\"transactions\":[],\"portfolios\":[],\"bills\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("restored"))
                .andExpect(jsonPath("$.transactions").value(0))
                .andExpect(jsonPath("$.portfolios").value(0))
                .andExpect(jsonPath("$.bills").value(0));

        verify(backupService).restore(eq(userId), any(BackupPayload.class));
    }
}
