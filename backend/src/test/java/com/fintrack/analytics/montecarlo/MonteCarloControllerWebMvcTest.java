package com.fintrack.analytics.montecarlo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.analytics.montecarlo.dto.AllocationClassDefault;
import com.fintrack.analytics.montecarlo.dto.MonteCarloDefaultsResponse;
import com.fintrack.analytics.montecarlo.dto.MonteCarloResponse;
import com.fintrack.analytics.montecarlo.dto.MonteCarloSummary;
import com.fintrack.analytics.montecarlo.dto.YearPercentilePoint;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MonteCarloController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MonteCarloControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean MonteCarloService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private MonteCarloResponse stubResponse() {
        return new MonteCarloResponse(
                1,
                100,
                new BigDecimal("100000.00"),
                new BigDecimal("1000.00"),
                null,
                List.of(
                        new YearPercentilePoint(
                                1,
                                new BigDecimal("100.00"),
                                new BigDecimal("200.00"),
                                new BigDecimal("300.00"),
                                new BigDecimal("400.00"),
                                new BigDecimal("500.00"))),
                new MonteCarloSummary(
                        new BigDecimal("300.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("300.00"),
                        new BigDecimal("500.00"),
                        null),
                List.of(
                        new AllocationClassDefault(
                                AssetClass.STOCK,
                                BigDecimal.ONE,
                                new BigDecimal("0.07"),
                                new BigDecimal("0.18"))));
    }

    private MonteCarloDefaultsResponse stubDefaults() {
        return new MonteCarloDefaultsResponse(
                10000,
                20,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                List.of(
                        new AllocationClassDefault(
                                AssetClass.STOCK,
                                new BigDecimal("0.50"),
                                new BigDecimal("0.07"),
                                new BigDecimal("0.18"))));
    }

    private String validRequestJson() {
        return """
{
  "horizonYears": 1,
  "iterations": 100,
  "currentNetWorth": 100000,
  "monthlyContribution": 1000,
  "targetNetWorth": null,
  "allocations": [
    { "assetClass": "STOCK", "weight": 1.0, "annualMeanReturn": 0.07, "annualStdDev": 0.18 }
  ]
}
""";
    }

    @Test
    void postReturns200WithFan() throws Exception {
        stubAuthenticatedUser();
        when(service.compute(eq(userId), any())).thenReturn(stubResponse());

        mockMvc.perform(
                        post("/api/v1/analytics/monte-carlo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iterations").value(100))
                .andExpect(jsonPath("$.fan[0].year").value(1))
                .andExpect(jsonPath("$.summary.mean").value(300.00));
    }

    @Test
    void getDefaultsReturns200() throws Exception {
        stubAuthenticatedUser();
        when(service.defaults()).thenReturn(stubDefaults());

        mockMvc.perform(get("/api/v1/analytics/monte-carlo/defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultIterations").value(10000))
                .andExpect(jsonPath("$.defaultHorizonYears").value(20))
                .andExpect(jsonPath("$.classes[0].assetClass").value("STOCK"));
    }

    @Test
    void postWithMissingAllocationsReturns400() throws Exception {
        stubAuthenticatedUser();
        String body =
                """
                {
                  "horizonYears": 1,
                  "iterations": 100,
                  "currentNetWorth": 100000,
                  "monthlyContribution": 1000,
                  "allocations": []
                }
                """;
        mockMvc.perform(
                        post("/api/v1/analytics/monte-carlo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postWithIterationsOutOfRangeReturns400() throws Exception {
        stubAuthenticatedUser();
        String body =
                """
{
  "horizonYears": 1,
  "iterations": 99999,
  "currentNetWorth": 100000,
  "monthlyContribution": 1000,
  "allocations": [
    { "assetClass": "STOCK", "weight": 1.0, "annualMeanReturn": 0.07, "annualStdDev": 0.18 }
  ]
}
""";
        mockMvc.perform(
                        post("/api/v1/analytics/monte-carlo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postWithHorizonOutOfRangeReturns400() throws Exception {
        stubAuthenticatedUser();
        String body =
                """
{
  "horizonYears": 100,
  "iterations": 100,
  "currentNetWorth": 100000,
  "monthlyContribution": 1000,
  "allocations": [
    { "assetClass": "STOCK", "weight": 1.0, "annualMeanReturn": 0.07, "annualStdDev": 0.18 }
  ]
}
""";
        mockMvc.perform(
                        post("/api/v1/analytics/monte-carlo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postWithMissingHorizonReturns400() throws Exception {
        stubAuthenticatedUser();
        String body =
                """
{
  "iterations": 100,
  "currentNetWorth": 100000,
  "monthlyContribution": 1000,
  "allocations": [
    { "assetClass": "STOCK", "weight": 1.0, "annualMeanReturn": 0.07, "annualStdDev": 0.18 }
  ]
}
""";
        mockMvc.perform(
                        post("/api/v1/analytics/monte-carlo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postExposesServiceWiringForCacheLayer() throws Exception {
        // The cache annotation lives at the service surface; we cannot validate cache hits at the
        // controller boundary without a Spring context. This test pins the contract that the
        // controller delegates exactly once per HTTP call to the service.
        stubAuthenticatedUser();
        when(service.compute(eq(userId), any())).thenReturn(stubResponse());

        mockMvc.perform(
                        post("/api/v1/analytics/monte-carlo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson()))
                .andExpect(status().isOk());
        verify(service, times(1)).compute(eq(userId), any());
    }
}
