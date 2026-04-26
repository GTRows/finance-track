package com.fintrack.portfolio.transaction;

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
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.portfolio.transaction.dto.RecordTransactionRequest;
import com.fintrack.portfolio.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(controllers = InvestmentTransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InvestmentTransactionControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean InvestmentTransactionService transactionService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private TransactionResponse sample(UUID txnId, UUID portfolioId, UUID assetId) {
        return new TransactionResponse(
                txnId,
                portfolioId,
                assetId,
                "AAPL",
                "Apple",
                TxnType.BUY,
                new BigDecimal("10"),
                new BigDecimal("250.50"),
                new BigDecimal("2505.00"),
                new BigDecimal("5.00"),
                null,
                LocalDate.of(2026, 4, 1),
                Instant.parse("2026-04-01T10:00:00Z"));
    }

    @Test
    void listReturnsTransactions() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        when(transactionService.list(eq(userId), eq(pid)))
                .thenReturn(List.of(sample(txnId, pid, aid)));

        mockMvc.perform(get("/api/v1/portfolios/" + pid + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(txnId.toString()))
                .andExpect(jsonPath("$[0].txnType").value("BUY"));
    }

    @Test
    void recordReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        when(transactionService.record(eq(userId), eq(pid), any(RecordTransactionRequest.class)))
                .thenReturn(sample(txnId, pid, aid));

        mockMvc.perform(
                        post("/api/v1/portfolios/" + pid + "/transactions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + aid
                                                + "\",\"txnType\":\"BUY\",\"quantity\":10,\"priceTry\":250.50,\"feeTry\":5.00,\"txnDate\":\"2026-04-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(txnId.toString()));
    }

    @Test
    void recordRejectsMissingAssetId() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/portfolios/" + pid + "/transactions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"txnType\":\"BUY\",\"quantity\":10,\"priceTry\":250.50,\"txnDate\":\"2026-04-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void recordRejectsZeroQuantity() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/portfolios/" + pid + "/transactions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + aid
                                                + "\",\"txnType\":\"BUY\",\"quantity\":0,\"priceTry\":250.50,\"txnDate\":\"2026-04-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/portfolios/" + pid + "/transactions/" + txnId).with(csrf()))
                .andExpect(status().isNoContent());

        verify(transactionService).delete(eq(userId), eq(pid), eq(txnId));
    }
}
