package com.fintrack.auth;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EmailVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmailVerificationControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean EmailVerificationService emailVerificationService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void confirmAcceptsToken() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/email-verify/confirm")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verified"));

        verify(emailVerificationService).confirm(eq("abc"));
    }

    @Test
    void confirmRejectsBlankToken() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/email-verify/confirm")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("missing_token"));
    }

    @Test
    void resendQueuesEmail() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(post("/api/v1/auth/email-verify/resend").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("queued"));

        verify(emailVerificationService).sendVerification(eq(userId));
    }
}
