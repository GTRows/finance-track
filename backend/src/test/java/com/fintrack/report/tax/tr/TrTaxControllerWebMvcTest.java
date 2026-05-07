package com.fintrack.report.tax.tr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

@WebMvcTest(controllers = TrTaxController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TrTaxControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean TrTaxService trTaxService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private TrTaxReportResponse populatedResponse(int year) {
        TrTaxParameters params =
                new TrTaxParameters(
                        year,
                        new BigDecimal("158000"),
                        List.of("STOCK"),
                        new BigDecimal("0.15"),
                        new BigDecimal("0.10"),
                        "test",
                        "test");
        return new TrTaxReportResponse(
                year,
                params,
                new TrTaxReportResponse.DividendStoppage(
                        new BigDecimal("1000"),
                        new BigDecimal("150"),
                        new BigDecimal("850"),
                        List.of()),
                new TrTaxReportResponse.CapitalGainsThreshold(
                        new BigDecimal("100000"),
                        new BigDecimal("158000"),
                        new BigDecimal("58000"),
                        new BigDecimal("0.6329"),
                        "under"),
                List.of());
    }

    @Test
    void reportReturnsOkWithBody() throws Exception {
        stubAuthenticatedUser();
        when(trTaxService.compute(eq(userId), eq(2025))).thenReturn(populatedResponse(2025));

        mockMvc.perform(get("/api/v1/reports/tax/tr").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.parameters.capitalGainsThresholdTry").value(158000))
                .andExpect(jsonPath("$.capitalGains.status").value("under"))
                .andExpect(jsonPath("$.capitalGains.headroomTry").value(58000));
    }

    @Test
    void reportYearOmittedDelegatesNullToService() throws Exception {
        stubAuthenticatedUser();
        when(trTaxService.compute(eq(userId), ArgumentMatchers.<Integer>isNull()))
                .thenReturn(populatedResponse(2026));

        mockMvc.perform(get("/api/v1/reports/tax/tr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026));

        verify(trTaxService).compute(eq(userId), ArgumentMatchers.<Integer>isNull());
    }

    @Test
    void reportRejectsYearBelowMinimum() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/reports/tax/tr").param("year", "-5"))
                .andExpect(status().isBadRequest());

        verify(trTaxService, never()).compute(any(), any());
    }

    @Test
    void reportFailsWhenPrincipalMissing() throws Exception {
        // No stubAuthenticatedUser() call — the @AuthenticationPrincipal resolver yields null
        // because addFilters=false bypasses the JWT chain. This documents the controller's
        // dependency on a populated principal; in production SecurityConfig produces 401 first.
        mockMvc.perform(get("/api/v1/reports/tax/tr")).andExpect(status().is5xxServerError());

        verify(trTaxService, never()).compute(any(), any());
    }
}
