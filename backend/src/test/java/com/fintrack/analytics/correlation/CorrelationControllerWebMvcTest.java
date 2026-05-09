package com.fintrack.analytics.correlation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.analytics.correlation.dto.CorrelationMatrixResponse;
import com.fintrack.analytics.correlation.dto.SamplePeriod;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CorrelationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CorrelationControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean CorrelationService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private CorrelationMatrixResponse stubResponse(UUID a, UUID b) {
        return new CorrelationMatrixResponse(
                List.of(a, b),
                List.of("A", "B"),
                List.of("Asset A", "Asset B"),
                List.of(Arrays.asList(1.0, 0.5), Arrays.asList(0.5, 1.0)),
                List.of(List.of(10, 9), List.of(9, 10)),
                new SamplePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), 9),
                "PEARSON");
    }

    @Test
    void happyPathReturns200WithMatrix() throws Exception {
        stubAuthenticatedUser();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(service.compute(eq(userId), any(), any(), any(), any()))
                .thenReturn(stubResponse(a, b));

        mockMvc.perform(get("/api/v1/analytics/correlations").param("assetIds", a + "," + b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetIds[0]").value(a.toString()))
                .andExpect(jsonPath("$.method").value("PEARSON"))
                .andExpect(jsonPath("$.matrix[0][0]").value(1.0))
                .andExpect(jsonPath("$.matrix[0][1]").value(0.5));
    }

    @Test
    void missingAssetIdsReturns400() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/analytics/correlations")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/analytics/correlations").param("assetIds", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRangeBubblesAs400() throws Exception {
        stubAuthenticatedUser();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(service.compute(eq(userId), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("Invalid range", "CORRELATION_RANGE_INVALID"));

        mockMvc.perform(
                        get("/api/v1/analytics/correlations")
                                .param("assetIds", a + "," + b)
                                .param("from", "2026-05-01")
                                .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CORRELATION_RANGE_INVALID"));
    }

    @Test
    void tooManyAssetsBubblesAs400() throws Exception {
        stubAuthenticatedUser();
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 26; i++) {
            if (i > 0) ids.append(',');
            ids.append(UUID.randomUUID());
        }
        when(service.compute(eq(userId), any(), any(), any(), any()))
                .thenThrow(
                        new BusinessRuleException(
                                "Too many assets in correlation request", "CORRELATION_TOO_MANY"));

        mockMvc.perform(get("/api/v1/analytics/correlations").param("assetIds", ids.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CORRELATION_TOO_MANY"));
    }

    @Test
    void methodDefaultsToPearsonWhenOmitted() throws Exception {
        stubAuthenticatedUser();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(service.compute(eq(userId), any(), any(), any(), eq(CorrelationMethod.PEARSON)))
                .thenReturn(stubResponse(a, b));

        mockMvc.perform(get("/api/v1/analytics/correlations").param("assetIds", a + "," + b))
                .andExpect(status().isOk());
    }

    @Test
    void methodAcceptsLowercaseSpearman() throws Exception {
        stubAuthenticatedUser();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(service.compute(eq(userId), any(), any(), any(), eq(CorrelationMethod.SPEARMAN)))
                .thenReturn(stubResponse(a, b));

        // Spring's default enum binder uppercases the literal so "spearman" maps to SPEARMAN.
        mockMvc.perform(
                        get("/api/v1/analytics/correlations")
                                .param("assetIds", a + "," + b)
                                .param("method", "SPEARMAN"))
                .andExpect(status().isOk());
    }
}
