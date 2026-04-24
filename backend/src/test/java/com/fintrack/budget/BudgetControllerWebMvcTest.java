package com.fintrack.budget;

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
import com.fintrack.budget.dto.BudgetSummaryResponse;
import com.fintrack.budget.dto.CreateTransactionRequest;
import com.fintrack.budget.dto.MonthlySummaryResponse;
import com.fintrack.budget.dto.TransactionResponse;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.exception.GlobalExceptionHandler;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BudgetController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BudgetControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean BudgetService budgetService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private TransactionResponse sampleTxn() {
        return new TransactionResponse(
                UUID.randomUUID(),
                BudgetTransaction.TxnType.EXPENSE,
                new BigDecimal("100"),
                "TRY",
                null,
                null,
                null,
                null,
                null,
                "Food",
                LocalDate.of(2026, 4, 15),
                false,
                List.of(),
                false,
                Instant.parse("2026-04-15T12:00:00Z"));
    }

    @Test
    void listTransactionsReturnsPage() throws Exception {
        stubAuthenticatedUser();
        when(budgetService.listTransactions(
                        eq(userId), eq("2026-04"), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleTxn())));

        mockMvc.perform(get("/api/v1/budget/transactions").param("month", "2026-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void listTransactionsRejectsMissingMonth() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/budget/transactions")).andExpect(status().is5xxServerError());
    }

    @Test
    void createReturns201WithTransaction() throws Exception {
        stubAuthenticatedUser();
        when(budgetService.create(eq(userId), any(CreateTransactionRequest.class)))
                .thenReturn(sampleTxn());

        mockMvc.perform(
                        post("/api/v1/budget/transactions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"txnType\":\"EXPENSE\",\"amount\":100,\"txnDate\":\"2026-04-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.txnType").value("EXPENSE"));
    }

    @Test
    void createRejectsMissingTxnType() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/budget/transactions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":100,\"txnDate\":\"2026-04-15\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsNonPositiveAmount() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/budget/transactions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"txnType\":\"EXPENSE\",\"amount\":0,\"txnDate\":\"2026-04-15\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsMissingDate() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/budget/transactions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"txnType\":\"EXPENSE\",\"amount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID txnId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/budget/transactions/" + txnId).with(csrf()))
                .andExpect(status().isNoContent());

        verify(budgetService).delete(eq(userId), eq(txnId));
    }

    @Test
    void bulkDeleteReturnsCount() throws Exception {
        stubAuthenticatedUser();
        when(budgetService.bulkDelete(eq(userId), any())).thenReturn(3);

        mockMvc.perform(
                        post("/api/v1/budget/transactions/bulk-delete")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"ids\":[\""
                                                + UUID.randomUUID()
                                                + "\",\""
                                                + UUID.randomUUID()
                                                + "\",\""
                                                + UUID.randomUUID()
                                                + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(3));
    }

    @Test
    void summaryReturnsBudgetSummary() throws Exception {
        stubAuthenticatedUser();
        BudgetSummaryResponse summary =
                new BudgetSummaryResponse(
                        "2026-04",
                        new BigDecimal("10000"),
                        new BigDecimal("6000"),
                        new BigDecimal("4000"),
                        new BigDecimal("40.00"),
                        List.of(),
                        List.of());
        when(budgetService.summary(eq(userId), eq("2026-04"))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/budget/summary").param("month", "2026-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(10000))
                .andExpect(jsonPath("$.savingsRate").value(40.00));
    }

    @Test
    void snapshotReturns201() throws Exception {
        stubAuthenticatedUser();
        MonthlySummaryResponse snap =
                new MonthlySummaryResponse(
                        "2026-04",
                        new BigDecimal("10000"),
                        new BigDecimal("6000"),
                        new BigDecimal("4000"),
                        new BigDecimal("40.00"),
                        null,
                        Instant.now());
        when(budgetService.captureSnapshot(eq(userId), eq("2026-04"))).thenReturn(snap);

        mockMvc.perform(post("/api/v1/budget/summaries/2026-04/snapshot").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.period").value("2026-04"));
    }

    @Test
    void summariesReturnsList() throws Exception {
        stubAuthenticatedUser();
        when(budgetService.listSummaries(eq(userId))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budget/summaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
