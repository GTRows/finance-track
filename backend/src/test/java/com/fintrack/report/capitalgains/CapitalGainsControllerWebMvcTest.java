package com.fintrack.report.capitalgains;

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

@WebMvcTest(controllers = CapitalGainsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CapitalGainsControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean CapitalGainsService capitalGainsService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void reportWithoutYearReturnsAggregate() throws Exception {
        stubAuthenticatedUser();
        when(capitalGainsService.compute(eq(userId), ArgumentMatchers.<Integer>isNull()))
                .thenReturn(
                        new CapitalGainsResponse(
                                null,
                                new BigDecimal("10000"),
                                new BigDecimal("8000"),
                                new BigDecimal("50"),
                                new BigDecimal("1950"),
                                BigDecimal.ZERO,
                                List.of(),
                                List.of()));

        mockMvc.perform(get("/api/v1/reports/capital-gains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedGain").value(1950));
    }

    @Test
    void reportWithYearPassesParam() throws Exception {
        stubAuthenticatedUser();
        when(capitalGainsService.compute(eq(userId), eq(2025)))
                .thenReturn(
                        new CapitalGainsResponse(
                                2025,
                                new BigDecimal("5000"),
                                new BigDecimal("4000"),
                                new BigDecimal("20"),
                                new BigDecimal("980"),
                                BigDecimal.ZERO,
                                List.of(),
                                List.of()));

        mockMvc.perform(get("/api/v1/reports/capital-gains").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.realizedGain").value(980));
    }
}
