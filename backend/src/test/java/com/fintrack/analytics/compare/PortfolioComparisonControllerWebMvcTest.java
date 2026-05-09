package com.fintrack.analytics.compare;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.analytics.compare.dto.PortfolioComparisonPoint;
import com.fintrack.analytics.compare.dto.PortfolioComparisonResponse;
import com.fintrack.analytics.compare.dto.PortfolioComparisonSeries;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PortfolioComparisonController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PortfolioComparisonControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean PortfolioComparisonService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private PortfolioComparisonResponse stubResponse(UUID portfolioId) {
        PortfolioComparisonPoint point =
                new PortfolioComparisonPoint(
                        LocalDate.of(2026, 4, 1),
                        new BigDecimal("100"),
                        new BigDecimal("80"),
                        new BigDecimal("20"),
                        new BigDecimal("0"),
                        new BigDecimal("20"));
        return new PortfolioComparisonResponse(
                "TRY", List.of(new PortfolioComparisonSeries(portfolioId, "Main", List.of(point))));
    }

    @Test
    void happyPathReturns200WithSeries() throws Exception {
        stubAuthenticatedUser();
        UUID p1 = UUID.randomUUID();
        when(service.compare(eq(userId), any(), any(), any())).thenReturn(stubResponse(p1));

        mockMvc.perform(get("/api/v1/analytics/portfolios/compare").param("ids", p1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.series[0].portfolioId").value(p1.toString()))
                .andExpect(jsonPath("$.series[0].points[0].totalValueTry").value(100));
    }

    @Test
    void missingIdsParamReturns400() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/analytics/portfolios/compare"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/analytics/portfolios/compare").param("ids", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRangeBubblesAs400() throws Exception {
        stubAuthenticatedUser();
        UUID p1 = UUID.randomUUID();
        when(service.compare(eq(userId), any(), any(), any()))
                .thenThrow(new BusinessRuleException("Invalid range", "COMPARE_RANGE_INVALID"));

        mockMvc.perform(
                        get("/api/v1/analytics/portfolios/compare")
                                .param("ids", p1.toString())
                                .param("from", "2026-05-01")
                                .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPARE_RANGE_INVALID"));
    }

    @Test
    void honoursFromAndToParams() throws Exception {
        stubAuthenticatedUser();
        UUID p1 = UUID.randomUUID();
        when(service.compare(
                        eq(userId),
                        any(),
                        eq(LocalDate.of(2026, 1, 1)),
                        eq(LocalDate.of(2026, 4, 1))))
                .thenReturn(stubResponse(p1));

        mockMvc.perform(
                        get("/api/v1/analytics/portfolios/compare")
                                .param("ids", p1.toString())
                                .param("from", "2026-01-01")
                                .param("to", "2026-04-01"))
                .andExpect(status().isOk());
    }

    @Test
    void multipleIdsParsedAsCommaSeparatedList() throws Exception {
        stubAuthenticatedUser();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(service.compare(eq(userId), any(), any(), any())).thenReturn(stubResponse(p1));

        mockMvc.perform(
                        get("/api/v1/analytics/portfolios/compare")
                                .param("ids", p1.toString() + "," + p2.toString()))
                .andExpect(status().isOk());
    }
}
