package com.fintrack.budget.recurring;

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
import com.fintrack.budget.recurring.dto.RecurringTemplateResponse;
import com.fintrack.budget.recurring.dto.UpsertRecurringRequest;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
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

@WebMvcTest(controllers = RecurringTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RecurringTemplateControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean RecurringTemplateService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private RecurringTemplateResponse sample(UUID id) {
        return new RecurringTemplateResponse(
                id,
                BudgetTransaction.TxnType.EXPENSE,
                new BigDecimal("100"),
                null,
                null,
                "Rent",
                15,
                true,
                null,
                null);
    }

    @Test
    void listReturnsTemplates() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.listForUser(eq(userId))).thenReturn(List.of(sample(id)));

        mockMvc.perform(get("/api/v1/budget/recurring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].description").value("Rent"));
    }

    @Test
    void createReturnsTemplate() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.create(eq(userId), any(UpsertRecurringRequest.class))).thenReturn(sample(id));

        mockMvc.perform(
                        post("/api/v1/budget/recurring")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"txnType\":\"EXPENSE\",\"amount\":100,\"dayOfMonth\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createRejectsDayOutOfRange() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/budget/recurring")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"txnType\":\"EXPENSE\",\"amount\":100,\"dayOfMonth\":32}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsZeroAmount() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/budget/recurring")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"txnType\":\"EXPENSE\",\"amount\":0,\"dayOfMonth\":15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateReturnsTemplate() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.update(eq(userId), eq(id), any(UpsertRecurringRequest.class)))
                .thenReturn(sample(id));

        mockMvc.perform(
                        put("/api/v1/budget/recurring/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"txnType\":\"EXPENSE\",\"amount\":120,\"dayOfMonth\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/budget/recurring/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).delete(eq(userId), eq(id));
    }

    @Test
    void runNowReturnsTemplate() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.runNow(eq(userId), eq(id))).thenReturn(sample(id));

        mockMvc.perform(post("/api/v1/budget/recurring/" + id + "/run-now").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }
}
