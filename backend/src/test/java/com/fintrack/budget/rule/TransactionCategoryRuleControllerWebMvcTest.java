package com.fintrack.budget.rule;

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
import com.fintrack.budget.rule.dto.CategoryRuleResponse;
import com.fintrack.budget.rule.dto.UpsertCategoryRuleRequest;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.time.Instant;
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

@WebMvcTest(controllers = TransactionCategoryRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TransactionCategoryRuleControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean TransactionCategoryRuleService ruleService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private CategoryRuleResponse sample(UUID id) {
        return new CategoryRuleResponse(
                id,
                "starbucks",
                UUID.randomUUID(),
                "Cafes",
                "#A0522D",
                BudgetTransaction.TxnType.EXPENSE,
                10,
                3,
                Instant.parse("2026-04-01T10:00:00Z"));
    }

    @Test
    void listReturnsRules() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(ruleService.list(eq(userId))).thenReturn(List.of(sample(id)));

        mockMvc.perform(get("/api/v1/budget/category-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].pattern").value("starbucks"));
    }

    @Test
    void createReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        when(ruleService.create(eq(userId), any(UpsertCategoryRuleRequest.class)))
                .thenReturn(sample(id));

        mockMvc.perform(
                        post("/api/v1/budget/category-rules")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"pattern\":\"starbucks\",\"categoryId\":\""
                                                + cid
                                                + "\",\"txnType\":\"EXPENSE\",\"priority\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createRejectsBlankPattern() throws Exception {
        stubAuthenticatedUser();
        UUID cid = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/budget/category-rules")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"pattern\":\"\",\"categoryId\":\""
                                                + cid
                                                + "\",\"txnType\":\"EXPENSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateReturnsRule() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        when(ruleService.update(eq(userId), eq(id), any(UpsertCategoryRuleRequest.class)))
                .thenReturn(sample(id));

        mockMvc.perform(
                        put("/api/v1/budget/category-rules/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"pattern\":\"starbucks\",\"categoryId\":\""
                                                + cid
                                                + "\",\"txnType\":\"EXPENSE\",\"priority\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/budget/category-rules/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(ruleService).delete(eq(userId), eq(id));
    }
}
