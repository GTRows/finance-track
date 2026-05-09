package com.fintrack.portfolio.rebalance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.common.exception.RebalanceConflictException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.rebalance.dto.RebalanceCommitRequest;
import com.fintrack.portfolio.rebalance.dto.RebalanceCommitResult;
import com.fintrack.portfolio.rebalance.dto.RebalancePreview;
import com.fintrack.portfolio.rebalance.dto.RebalancePreviewRequest;
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

@WebMvcTest(controllers = RebalanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RebalanceControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RebalanceService rebalanceService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private RebalancePreview emptyPreview() {
        return new RebalancePreview(
                UUID.randomUUID(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1.00"),
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                Instant.now());
    }

    @Test
    void preview_200_happy() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(rebalanceService.preview(eq(userId), eq(portfolioId), any()))
                .thenReturn(emptyPreview());
        String body = objectMapper.writeValueAsString(new RebalancePreviewRequest(accountId, null));

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/preview", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalId").exists());
    }

    @Test
    void preview_400_missingAccountId() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        String body = "{}";

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/preview", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_404_unknownPortfolio() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(rebalanceService.preview(eq(userId), eq(portfolioId), any()))
                .thenThrow(new ResourceNotFoundException("Portfolio not found"));
        String body = objectMapper.writeValueAsString(new RebalancePreviewRequest(accountId, null));

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/preview", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void preview_400_invalidThresholdOverride() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String body = "{\"accountId\":\"" + accountId + "\",\"driftThresholdOverride\":99.99}";

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/preview", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commit_200_happy() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(rebalanceService.commit(eq(userId), eq(portfolioId), any()))
                .thenReturn(
                        new RebalanceCommitResult(
                                proposalId, 2, List.of(UUID.randomUUID(), UUID.randomUUID())));
        String body =
                objectMapper.writeValueAsString(
                        new RebalanceCommitRequest(proposalId, accountId, List.of(0, 1)));

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/commit", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committedCount").value(2));
    }

    @Test
    void commit_409_staleProposal() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(rebalanceService.commit(eq(userId), eq(portfolioId), any()))
                .thenThrow(new RebalanceConflictException("stale", "REBALANCE_PROPOSAL_STALE"));
        String body =
                objectMapper.writeValueAsString(
                        new RebalanceCommitRequest(proposalId, accountId, List.of(0)));

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/commit", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REBALANCE_PROPOSAL_STALE"));
    }

    @Test
    void commit_409_alreadyCommitted() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(rebalanceService.commit(eq(userId), eq(portfolioId), any()))
                .thenThrow(
                        new RebalanceConflictException(
                                "already", "REBALANCE_PROPOSAL_ALREADY_COMMITTED"));
        String body =
                objectMapper.writeValueAsString(
                        new RebalanceCommitRequest(proposalId, accountId, List.of(0)));

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/commit", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REBALANCE_PROPOSAL_ALREADY_COMMITTED"));
    }

    @Test
    void commit_404_unknownProposalId() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(rebalanceService.commit(eq(userId), eq(portfolioId), any()))
                .thenThrow(new ResourceNotFoundException("Proposal not found"));
        String body =
                objectMapper.writeValueAsString(
                        new RebalanceCommitRequest(proposalId, accountId, List.of(0)));

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/commit", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void commit_400_emptySelections() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        String body =
                objectMapper.writeValueAsString(
                        new RebalanceCommitRequest(proposalId, accountId, List.of()));

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/commit", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commit_400_selectionsOutOfRange() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(rebalanceService.commit(eq(userId), eq(portfolioId), any()))
                .thenThrow(new BusinessRuleException("out", "REBALANCE_SELECTION_OUT_OF_RANGE"));
        String body =
                objectMapper.writeValueAsString(
                        new RebalanceCommitRequest(proposalId, accountId, List.of(99)));

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/commit", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REBALANCE_SELECTION_OUT_OF_RANGE"));
    }

    @Test
    void preview_400_onEmptyJson() throws Exception {
        stubAuthenticatedUser();
        UUID portfolioId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/portfolios/{id}/rebalance/preview", portfolioId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
