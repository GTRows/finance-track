package com.fintrack.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "logging.file.path=/nonexistent-log-dir-for-tests")
class AdminControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean AdminSettingRepository adminSettingRepository;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void listLogFilesReturnsEmptyWhenDirectoryMissing() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/admin/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void logStatsReturnsZerosWhenDirectoryMissing() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/admin/logs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSizeBytes").value(0))
                .andExpect(jsonPath("$.fileCount").value(0));
    }

    @Test
    void downloadLogFileReturns404WhenMissing() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/admin/logs/nonexistent.log")).andExpect(status().isNotFound());
    }

    @Test
    void getSettingsReturnsEmptyList() throws Exception {
        stubAuthenticatedUser();
        org.mockito.Mockito.when(adminSettingRepository.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
