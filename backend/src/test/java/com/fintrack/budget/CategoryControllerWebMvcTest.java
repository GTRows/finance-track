package com.fintrack.budget;

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
import com.fintrack.budget.dto.CategoriesResponse;
import com.fintrack.budget.dto.CategoryResponse;
import com.fintrack.budget.dto.CreateCategoryRequest;
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

@WebMvcTest(controllers = CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CategoryControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean CategoryService categoryService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private CategoryResponse sample(String name, BigDecimal budget) {
        return new CategoryResponse(UUID.randomUUID(), name, null, null, budget, false);
    }

    @Test
    void listReturnsBothGroups() throws Exception {
        stubAuthenticatedUser();
        when(categoryService.listAll(eq(userId)))
                .thenReturn(
                        new CategoriesResponse(
                                List.of(sample("Salary", null)),
                                List.of(sample("Food", new BigDecimal("500")))));

        mockMvc.perform(get("/api/v1/budget/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income[0].name").value("Salary"))
                .andExpect(jsonPath("$.expense[0].budgetAmount").value(500));
    }

    @Test
    void createIncomeReturns201() throws Exception {
        stubAuthenticatedUser();
        when(categoryService.createIncome(eq(userId), any(CreateCategoryRequest.class)))
                .thenReturn(sample("Salary", null));

        mockMvc.perform(
                        post("/api/v1/budget/categories/income")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Salary\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Salary"));
    }

    @Test
    void createIncomeRejectsBlankName() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/budget/categories/income")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createExpenseReturns201WithBudget() throws Exception {
        stubAuthenticatedUser();
        when(categoryService.createExpense(eq(userId), any(CreateCategoryRequest.class)))
                .thenReturn(sample("Food", new BigDecimal("500")));

        mockMvc.perform(
                        post("/api/v1/budget/categories/expense")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Food\",\"budgetAmount\":500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.budgetAmount").value(500));
    }

    @Test
    void updateIncomeReturns200() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(categoryService.updateIncome(eq(userId), eq(id), any(CreateCategoryRequest.class)))
                .thenReturn(sample("Updated", null));

        mockMvc.perform(
                        put("/api/v1/budget/categories/income/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void updateExpenseReturns200() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(categoryService.updateExpense(eq(userId), eq(id), any(CreateCategoryRequest.class)))
                .thenReturn(sample("Updated", new BigDecimal("750")));

        mockMvc.perform(
                        put("/api/v1/budget/categories/expense/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Updated\",\"budgetAmount\":750}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetAmount").value(750));
    }

    @Test
    void deleteIncomeReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/budget/categories/income/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(categoryService).deleteIncome(eq(userId), eq(id));
    }

    @Test
    void deleteExpenseReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/budget/categories/expense/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(categoryService).deleteExpense(eq(userId), eq(id));
    }
}
