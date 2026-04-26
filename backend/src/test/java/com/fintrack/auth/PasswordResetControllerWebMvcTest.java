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

@WebMvcTest(controllers = PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PasswordResetControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean PasswordResetService passwordResetService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void requestQueuesReset() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/password-reset/request")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"u@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("queued"));

        verify(passwordResetService).requestReset(eq("u@example.com"));
    }

    @Test
    void requestRejectsInvalidEmail() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/password-reset/request")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void confirmResetsPassword() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/password-reset/confirm")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"tok\",\"newPassword\":\"newPassw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reset"));

        verify(passwordResetService).confirmReset(eq("tok"), eq("newPassw0rd!"));
    }

    @Test
    void confirmRejectsShortPassword() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/password-reset/confirm")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"tok\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
