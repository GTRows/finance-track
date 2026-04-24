package com.fintrack.portfolio.allocation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.portfolio.allocation.dto.AllocationSummary;
import com.fintrack.portfolio.allocation.dto.SetAllocationRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AllocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AllocationControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean AllocationService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void getReturnsSummary() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(service.summarize(eq(userId), eq(pid)))
                .thenReturn(new AllocationSummary(BigDecimal.ZERO, false, List.of()));

        mockMvc.perform(get("/api/v1/portfolios/" + pid + "/allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void putReplacesTargets() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(service.replaceTargets(eq(userId), eq(pid), any(SetAllocationRequest.class)))
                .thenReturn(new AllocationSummary(new BigDecimal("1000"), true, List.of()));

        mockMvc.perform(
                        put("/api/v1/portfolios/" + pid + "/allocation")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"targets\":[{\"assetType\":\"CRYPTO\",\"targetPercent\":100}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void putRejectsMissingTargets() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();

        mockMvc.perform(
                        put("/api/v1/portfolios/" + pid + "/allocation")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
