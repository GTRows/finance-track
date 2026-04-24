package com.fintrack.alert;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.alert.dto.NotificationResponse;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.entity.AlertNotification;
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

@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean NotificationService notificationService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private NotificationResponse sample(UUID id) {
        return new NotificationResponse(
                id,
                null,
                null,
                AlertNotification.SourceType.PRICE_ALERT,
                null,
                "hello",
                null,
                Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void listReturnsNotifications() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(notificationService.list(eq(userId))).thenReturn(List.of(sample(id)));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].message").value("hello"));
    }

    @Test
    void unreadCountReturnsCount() throws Exception {
        stubAuthenticatedUser();
        when(notificationService.unreadCount(eq(userId))).thenReturn(7L);

        mockMvc.perform(get("/api/v1/notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7));
    }

    @Test
    void markReadReturnsNotification() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(notificationService.markAsRead(eq(userId), eq(id))).thenReturn(sample(id));

        mockMvc.perform(post("/api/v1/notifications/" + id + "/read").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void markAllReadReturns204() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(post("/api/v1/notifications/read-all").with(csrf()))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllAsRead(eq(userId));
    }
}
