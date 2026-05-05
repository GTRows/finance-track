package com.fintrack.audit;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuditRetentionControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean AuditLogRepository repository;
    @MockBean AuditRetentionProperties properties;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void getRetentionAsAdminReturnsConfigAndStats() throws Exception {
        stubAuthenticatedUser();
        when(properties.retentionDays()).thenReturn(90);
        when(properties.batchSize()).thenReturn(1000);
        when(properties.enabled()).thenReturn(true);
        when(repository.findOldestCreatedAt())
                .thenReturn(Optional.of(Instant.parse("2026-01-01T00:00:00Z")));
        when(repository.count()).thenReturn(42L);

        mockMvc.perform(get("/api/v1/admin/audit/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionDays").value(90))
                .andExpect(jsonPath("$.batchSize").value(1000))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.oldestEntry").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.totalEntries").value(42));
    }

    @Test
    void getRetentionWithEmptyTableReturnsNullOldest() throws Exception {
        stubAuthenticatedUser();
        when(properties.retentionDays()).thenReturn(90);
        when(properties.batchSize()).thenReturn(1000);
        when(properties.enabled()).thenReturn(true);
        when(repository.findOldestCreatedAt()).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(0L);

        mockMvc.perform(get("/api/v1/admin/audit/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").value(0))
                .andExpect(jsonPath("$.oldestEntry").doesNotExist());
    }
}
