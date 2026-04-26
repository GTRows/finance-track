package com.fintrack.portfolio.risk;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.portfolio.risk.dto.RiskMetricsResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RiskController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RiskControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean RiskService riskService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void getReturnsMetrics() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(riskService.compute(eq(userId), eq(pid), ArgumentMatchers.<BigDecimal>isNull()))
                .thenReturn(
                        new RiskMetricsResponse(
                                30,
                                LocalDate.of(2026, 3, 1),
                                LocalDate.of(2026, 3, 30),
                                new BigDecimal("0.05"),
                                new BigDecimal("0.18"),
                                new BigDecimal("0.42"),
                                new BigDecimal("-0.03"),
                                new BigDecimal("0.02"),
                                new BigDecimal("-0.015"),
                                new BigDecimal("0.001"),
                                new BigDecimal("0.20"),
                                true));

        mockMvc.perform(get("/api/v1/portfolios/" + pid + "/risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotCount").value(30))
                .andExpect(jsonPath("$.sufficientData").value(true));
    }

    @Test
    void getWithRiskFreeRateOverridePassesParam() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(riskService.compute(eq(userId), eq(pid), eq(new BigDecimal("0.30"))))
                .thenReturn(
                        RiskMetricsResponse.insufficient(
                                3,
                                LocalDate.of(2026, 3, 1),
                                LocalDate.of(2026, 3, 3),
                                new BigDecimal("0.30")));

        mockMvc.perform(get("/api/v1/portfolios/" + pid + "/risk").param("riskFreeRate", "0.30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sufficientData").value(false))
                .andExpect(jsonPath("$.riskFreeRate").value(0.30));
    }
}
