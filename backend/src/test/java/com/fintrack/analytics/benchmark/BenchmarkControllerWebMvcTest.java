package com.fintrack.analytics.benchmark;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.analytics.benchmark.dto.BenchmarkSeries;
import com.fintrack.analytics.benchmark.dto.BenchmarkSeriesResponse;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BenchmarkController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BenchmarkControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean BenchmarkService benchmarkService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void fetchUsesDefaultDays() throws Exception {
        stubAuthenticatedUser();
        when(benchmarkService.fetch(365))
                .thenReturn(
                        new BenchmarkSeriesResponse(
                                365,
                                List.of(
                                        new BenchmarkSeries(
                                                "BIST100",
                                                "XU100",
                                                "TRY",
                                                List.of(
                                                        new BenchmarkSeries.Point(
                                                                LocalDate.of(2026, 4, 1),
                                                                new BigDecimal("9500")))))));

        mockMvc.perform(get("/api/v1/analytics/benchmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(365))
                .andExpect(jsonPath("$.series[0].code").value("BIST100"));
    }

    @Test
    void fetchOverridesDays() throws Exception {
        stubAuthenticatedUser();
        when(benchmarkService.fetch(30)).thenReturn(new BenchmarkSeriesResponse(30, List.of()));

        mockMvc.perform(get("/api/v1/analytics/benchmarks").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.series.length()").value(0));
    }
}
