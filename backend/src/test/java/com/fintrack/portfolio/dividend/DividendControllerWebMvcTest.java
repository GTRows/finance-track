package com.fintrack.portfolio.dividend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.portfolio.dividend.dto.DividendResponse;
import com.fintrack.portfolio.dividend.dto.RecordDividendRequest;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DividendController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DividendControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean DividendService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private DividendResponse sample(UUID id, UUID portfolioId, UUID assetId) {
        return new DividendResponse(
                id,
                portfolioId,
                assetId,
                "AAPL",
                "Apple",
                new BigDecimal("0.24"),
                new BigDecimal("100"),
                new BigDecimal("24"),
                new BigDecimal("3.6"),
                new BigDecimal("20.4"),
                "USD",
                new BigDecimal("680"),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 3, 25),
                null);
    }

    @Test
    void listForPortfolioReturnsRows() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        UUID did = UUID.randomUUID();
        when(service.listForPortfolio(eq(userId), eq(pid)))
                .thenReturn(List.of(sample(did, pid, aid)));

        mockMvc.perform(get("/api/v1/portfolios/" + pid + "/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(did.toString()))
                .andExpect(jsonPath("$[0].assetSymbol").value("AAPL"));
    }

    @Test
    void recordReturnsResponse() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        UUID did = UUID.randomUUID();
        when(service.record(eq(userId), eq(pid), any(RecordDividendRequest.class)))
                .thenReturn(sample(did, pid, aid));

        mockMvc.perform(
                        post("/api/v1/portfolios/" + pid + "/dividends")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + aid
                                                + "\",\"grossAmount\":24,\"paymentDate\":\"2026-04-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(did.toString()));
    }

    @Test
    void recordRejectsMissingAssetId() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/portfolios/" + pid + "/dividends")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"grossAmount\":24,\"paymentDate\":\"2026-04-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void recordRejectsZeroGrossAmount() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/portfolios/" + pid + "/dividends")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + aid
                                                + "\",\"grossAmount\":0,\"paymentDate\":\"2026-04-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID did = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/portfolios/" + pid + "/dividends/" + did).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).delete(eq(userId), eq(pid), eq(did));
    }

    @Test
    void listForAssetReturnsRows() throws Exception {
        stubAuthenticatedUser();
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID did = UUID.randomUUID();
        when(service.listForAsset(eq(userId), eq(aid))).thenReturn(List.of(sample(did, pid, aid)));

        mockMvc.perform(get("/api/v1/assets/" + aid + "/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(did.toString()));
    }
}
