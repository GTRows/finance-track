package com.fintrack.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.audit.AuditService;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MailAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MailAdminControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean MailService mailService;
    @MockBean AuditService auditService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void statusReturnsEnabledFlag() throws Exception {
        stubAuthenticatedUser();
        when(mailService.isEnabled()).thenReturn(true);
        when(mailService.baseUrl()).thenReturn("https://app.example.com");

        mockMvc.perform(get("/api/v1/admin/mail/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.baseUrl").value("https://app.example.com"));
    }

    @Test
    void sendTestQueuesWhenEnabled() throws Exception {
        stubAuthenticatedUser();
        when(mailService.isEnabled()).thenReturn(true);

        mockMvc.perform(
                        post("/api/v1/admin/mail/test")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"to\":\"u@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.to").value("u@example.com"));

        verify(mailService).sendHtml(eq("u@example.com"), anyString(), anyString());
        verify(auditService).success(eq("MAIL_TEST"), eq(userId), anyString(), anyString());
    }

    @Test
    void sendTestReportsDisabledWhenSmtpOff() throws Exception {
        stubAuthenticatedUser();
        when(mailService.isEnabled()).thenReturn(false);

        mockMvc.perform(
                        post("/api/v1/admin/mail/test")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"to\":\"u@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));
    }
}
