package com.fintrack.analytics;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.analytics.dto.CashFlowProjectionResponse;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CashFlowProjectionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CashFlowProjectionControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean CashFlowProjectionService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void projectWithoutOverridesReturnsResponse() throws Exception {
        stubAuthenticatedUser();
        when(service.project(
                        eq(userId),
                        ArgumentMatchers.<Integer>isNull(),
                        ArgumentMatchers.<BigDecimal>isNull()))
                .thenReturn(
                        new CashFlowProjectionResponse(
                                new BigDecimal("20000"),
                                new BigDecimal("12000"),
                                new BigDecimal("8000"),
                                12,
                                true,
                                BigDecimal.ZERO,
                                List.of()));

        mockMvc.perform(get("/api/v1/analytics/cash-flow-projection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sufficient").value(true))
                .andExpect(jsonPath("$.avgMonthlyNet").value(8000));
    }

    @Test
    void projectWithOverridesPassesParams() throws Exception {
        stubAuthenticatedUser();
        when(service.project(eq(userId), eq(6), eq(new BigDecimal("5000"))))
                .thenReturn(
                        new CashFlowProjectionResponse(
                                new BigDecimal("18000"),
                                new BigDecimal("13000"),
                                new BigDecimal("5000"),
                                6,
                                true,
                                new BigDecimal("5000"),
                                List.of()));

        mockMvc.perform(
                        get("/api/v1/analytics/cash-flow-projection")
                                .param("months", "6")
                                .param("startingBalance", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingBalance").value(5000))
                .andExpect(jsonPath("$.sampleMonths").value(6));
    }
}
