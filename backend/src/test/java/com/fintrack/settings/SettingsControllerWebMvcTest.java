package com.fintrack.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.settings.dto.SettingsResponse;
import com.fintrack.settings.dto.UpdateSettingsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SettingsControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean SettingsService settingsService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void getReturnsSettings() throws Exception {
        stubAuthenticatedUser();
        when(settingsService.get(eq(userId)))
                .thenReturn(new SettingsResponse("TRY", "tr", "dark", "Europe/Istanbul", false));

        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
    }

    @Test
    void updateReturnsUpdatedSettings() throws Exception {
        stubAuthenticatedUser();
        when(settingsService.update(eq(userId), any(UpdateSettingsRequest.class)))
                .thenReturn(new SettingsResponse("USD", "en", "light", "Europe/Berlin", false));

        mockMvc.perform(
                        put("/api/v1/settings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currency\":\"USD\",\"language\":\"en\",\"theme\":\"light\",\"timezone\":\"Europe/Berlin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.theme").value("light"));
    }

    @Test
    void updateRejectsBadCurrencyFormat() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        put("/api/v1/settings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currency\":\"dollar\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateRejectsBadThemeValue() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        put("/api/v1/settings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"theme\":\"neon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void completeOnboardingReturnsSettings() throws Exception {
        stubAuthenticatedUser();
        when(settingsService.markOnboardingComplete(eq(userId)))
                .thenReturn(new SettingsResponse("TRY", "tr", "dark", "Europe/Istanbul", true));

        mockMvc.perform(post("/api/v1/settings/onboarding-complete").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }
}
