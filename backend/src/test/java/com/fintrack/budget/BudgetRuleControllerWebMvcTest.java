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
import com.fintrack.budget.dto.BudgetRuleResponse;
import com.fintrack.budget.dto.CreateBudgetRuleRequest;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
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

@WebMvcTest(controllers = BudgetRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BudgetRuleControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean BudgetRuleService ruleService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private BudgetRuleResponse sample(UUID id) {
        return new BudgetRuleResponse(
                id,
                UUID.randomUUID(),
                "Groceries",
                "#FF8800",
                new BigDecimal("3000"),
                new BigDecimal("1200"),
                new BigDecimal("0.40"),
                true,
                null,
                Instant.parse("2026-04-01T10:00:00Z"));
    }

    @Test
    void listReturnsRules() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(ruleService.listForUser(eq(userId))).thenReturn(List.of(sample(id)));

        mockMvc.perform(get("/api/v1/budget/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].categoryName").value("Groceries"));
    }

    @Test
    void createReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        when(ruleService.create(eq(userId), any(CreateBudgetRuleRequest.class)))
                .thenReturn(sample(id));

        mockMvc.perform(
                        post("/api/v1/budget/rules")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryId\":\""
                                                + cid
                                                + "\",\"monthlyLimitTry\":3000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createRejectsMissingCategoryId() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/budget/rules")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"monthlyLimitTry\":3000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsZeroLimit() throws Exception {
        stubAuthenticatedUser();
        UUID cid = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/budget/rules")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryId\":\"" + cid + "\",\"monthlyLimitTry\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/budget/rules/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(ruleService).delete(eq(userId), eq(id));
    }
}
