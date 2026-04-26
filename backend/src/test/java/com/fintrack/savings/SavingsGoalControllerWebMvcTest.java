package com.fintrack.savings;

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
import com.fintrack.savings.dto.ContributionRequest;
import com.fintrack.savings.dto.ContributionResponse;
import com.fintrack.savings.dto.GoalResponse;
import com.fintrack.savings.dto.UpsertGoalRequest;
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

@WebMvcTest(controllers = SavingsGoalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SavingsGoalControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean SavingsGoalService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private GoalResponse sampleGoal(UUID id) {
        return new GoalResponse(
                id,
                "Emergency Fund",
                new BigDecimal("60000"),
                LocalDate.of(2027, 1, 1),
                null,
                null,
                null,
                new BigDecimal("12000"),
                new BigDecimal("0.20"),
                new BigDecimal("4000"),
                LocalDate.of(2027, 6, 1),
                "ON_TRACK");
    }

    @Test
    void listReturnsGoals() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.list(eq(userId))).thenReturn(List.of(sampleGoal(id)));

        mockMvc.perform(get("/api/v1/savings/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Emergency Fund"));
    }

    @Test
    void createReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.create(eq(userId), any(UpsertGoalRequest.class))).thenReturn(sampleGoal(id));

        mockMvc.perform(
                        post("/api/v1/savings/goals")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Emergency"
                                            + " Fund\",\"targetAmount\":60000,\"targetDate\":\"2027-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/savings/goals")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"targetAmount\":60000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsZeroTarget() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/savings/goals")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Emergency Fund\",\"targetAmount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateReturnsGoal() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.update(eq(userId), eq(id), any(UpsertGoalRequest.class)))
                .thenReturn(sampleGoal(id));

        mockMvc.perform(
                        put("/api/v1/savings/goals/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Renamed\",\"targetAmount\":60000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void archiveReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/savings/goals/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).archive(eq(userId), eq(id));
    }

    @Test
    void contributionsReturnsRows() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        when(service.listContributions(eq(userId), eq(id)))
                .thenReturn(
                        List.of(
                                new ContributionResponse(
                                        cid,
                                        LocalDate.of(2026, 4, 1),
                                        new BigDecimal("500"),
                                        null)));

        mockMvc.perform(get("/api/v1/savings/goals/" + id + "/contributions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cid.toString()))
                .andExpect(jsonPath("$[0].amount").value(500));
    }

    @Test
    void addContributionReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        when(service.addContribution(eq(userId), eq(id), any(ContributionRequest.class)))
                .thenReturn(
                        new ContributionResponse(
                                cid, LocalDate.of(2026, 4, 1), new BigDecimal("500"), "April"));

        mockMvc.perform(
                        post("/api/v1/savings/goals/" + id + "/contributions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"contributionDate\":\"2026-04-01\",\"amount\":500,\"note\":\"April\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cid.toString()));
    }

    @Test
    void addContributionRejectsMissingDate() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/savings/goals/" + id + "/contributions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteContributionReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        UUID cid = UUID.randomUUID();

        mockMvc.perform(
                        delete("/api/v1/savings/goals/" + id + "/contributions/" + cid)
                                .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).deleteContribution(eq(userId), eq(id), eq(cid));
    }
}
