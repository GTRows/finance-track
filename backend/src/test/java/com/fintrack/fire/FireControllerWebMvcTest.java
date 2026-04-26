package com.fintrack.fire;

import static org.mockito.ArgumentMatchers.any;
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
import com.fintrack.fire.dto.FireResponse;
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

@WebMvcTest(controllers = FireController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FireControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean FireService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private FireResponse sample() {
        return new FireResponse(
                new BigDecimal("250000"),
                new BigDecimal("20000"),
                new BigDecimal("12000"),
                new BigDecimal("0.40"),
                new BigDecimal("8000"),
                new BigDecimal("0.04"),
                new BigDecimal("0.07"),
                new BigDecimal("3600000"),
                new BigDecimal("0.07"),
                240,
                new BigDecimal("20"),
                LocalDate.of(2046, 1, 1),
                12,
                true,
                List.of());
    }

    @Test
    void computeWithoutOverridesReturnsSummary() throws Exception {
        stubAuthenticatedUser();
        when(service.compute(eq(userId), any(), any(), any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/api/v1/fire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetNumber").value(3600000))
                .andExpect(jsonPath("$.sufficientData").value(true));
    }

    @Test
    void computeWithOverridesPassesParams() throws Exception {
        stubAuthenticatedUser();
        when(service.compute(
                        eq(userId),
                        eq(new BigDecimal("0.035")),
                        eq(new BigDecimal("0.06")),
                        eq(new BigDecimal("10000")),
                        eq(new BigDecimal("15000")),
                        eq(new BigDecimal("500000"))))
                .thenReturn(sample());

        mockMvc.perform(
                        get("/api/v1/fire")
                                .param("withdrawalRate", "0.035")
                                .param("expectedReturn", "0.06")
                                .param("monthlyContribution", "10000")
                                .param("monthlyExpense", "15000")
                                .param("netWorth", "500000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthsToFi").value(240));
    }
}
