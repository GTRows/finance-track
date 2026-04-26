package com.fintrack.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.entity.AuditLog;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuditControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean AuditLogRepository repository;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private AuditLog sample() {
        return AuditLog.builder()
                .id(1L)
                .userId(UUID.randomUUID())
                .username("ali")
                .action("LOGIN")
                .status(AuditLog.Status.SUCCESS)
                .ipAddress("127.0.0.1")
                .userAgent("ua")
                .detail("ok")
                .createdAt(Instant.parse("2026-04-01T10:00:00Z"))
                .build();
    }

    @Test
    void listReturnsPageWhenNoFilters() throws Exception {
        stubAuthenticatedUser();
        Page<AuditLog> page = new PageImpl<>(List.of(sample()), PageRequest.of(0, 50), 1);
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].action").value("LOGIN"));
    }

    @Test
    void listFiltersByUserId() throws Exception {
        stubAuthenticatedUser();
        UUID uid = UUID.randomUUID();
        Page<AuditLog> page = new PageImpl<>(List.of(sample()), PageRequest.of(0, 50), 1);
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(uid), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/audit").param("userId", uid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void listFiltersByAction() throws Exception {
        stubAuthenticatedUser();
        Page<AuditLog> page = new PageImpl<>(List.of(sample()), PageRequest.of(0, 50), 1);
        when(repository.findByActionOrderByCreatedAtDesc(eq("LOGIN"), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/audit").param("action", "LOGIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("LOGIN"));
    }

    @Test
    void listClampsExcessiveSize() throws Exception {
        stubAuthenticatedUser();
        Page<AuditLog> page = new PageImpl<>(List.of(sample()), PageRequest.of(0, 200), 1);
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/audit").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));
    }
}
