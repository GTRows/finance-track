package com.fintrack.debt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.debt.dto.DebtPaymentRequest;
import com.fintrack.debt.dto.DebtPaymentResponse;
import com.fintrack.debt.dto.DebtResponse;
import com.fintrack.debt.dto.UpsertDebtRequest;
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

@WebMvcTest(controllers = DebtController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DebtControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean DebtService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private DebtResponse sampleDebt(UUID id) {
        return new DebtResponse(
                id,
                "Mortgage",
                "MORTGAGE",
                new BigDecimal("100000"),
                new BigDecimal("0.1500"),
                120,
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("1500"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                LocalDate.of(2036, 1, 1),
                LocalDate.of(2036, 1, 1),
                BigDecimal.ZERO,
                0,
                "ON_TRACK",
                List.of());
    }

    @Test
    void listReturnsDebts() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.list(eq(userId))).thenReturn(List.of(sampleDebt(id)));

        mockMvc.perform(get("/api/v1/debts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Mortgage"));
    }

    @Test
    void createReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.create(eq(userId), any(UpsertDebtRequest.class))).thenReturn(sampleDebt(id));

        mockMvc.perform(
                        post("/api/v1/debts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Mortgage\",\"debtType\":\"MORTGAGE\",\"principal\":100000,\"annualRate\":0.15,\"termMonths\":120,\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/debts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"\",\"debtType\":\"MORTGAGE\",\"principal\":100000,\"annualRate\":0.15,\"termMonths\":120,\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsRateAboveOne() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/debts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Mortgage\",\"debtType\":\"MORTGAGE\",\"principal\":100000,\"annualRate\":1.5,\"termMonths\":120,\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateReturnsDebt() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.update(eq(userId), eq(id), any(UpsertDebtRequest.class)))
                .thenReturn(sampleDebt(id));

        mockMvc.perform(
                        put("/api/v1/debts/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Mortgage\",\"debtType\":\"MORTGAGE\",\"principal\":100000,\"annualRate\":0.15,\"termMonths\":120,\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void archiveReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/debts/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).archive(eq(userId), eq(id));
    }

    @Test
    void listPaymentsReturnsRows() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        when(service.listPayments(eq(userId), eq(id)))
                .thenReturn(
                        List.of(
                                new DebtPaymentResponse(
                                        pid,
                                        LocalDate.of(2026, 4, 1),
                                        new BigDecimal("1500"),
                                        null)));

        mockMvc.perform(get("/api/v1/debts/" + id + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pid.toString()))
                .andExpect(jsonPath("$[0].amount").value(1500));
    }

    @Test
    void addPaymentReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        when(service.addPayment(eq(userId), eq(id), any(DebtPaymentRequest.class)))
                .thenReturn(
                        new DebtPaymentResponse(
                                pid, LocalDate.of(2026, 4, 1), new BigDecimal("1500"), "April"));

        mockMvc.perform(
                        post("/api/v1/debts/" + id + "/payments")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"paymentDate\":\"2026-04-01\",\"amount\":1500,\"note\":\"April\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(pid.toString()));
    }

    @Test
    void addPaymentRejectsZeroAmount() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/debts/" + id + "/payments")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"paymentDate\":\"2026-04-01\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deletePaymentReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID pid = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/debts/" + id + "/payments/" + pid).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).deletePayment(eq(userId), eq(id), eq(pid));
    }
}
