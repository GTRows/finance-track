package com.fintrack.portfolio;

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
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.dto.CreatePortfolioRequest;
import com.fintrack.portfolio.dto.PortfolioResponse;
import com.fintrack.portfolio.dto.UpdatePortfolioRequest;
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

@WebMvcTest(controllers = PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PortfolioControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean PortfolioService portfolioService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private PortfolioResponse sample(UUID id, String name) {
        return new PortfolioResponse(
                id,
                name,
                Portfolio.PortfolioType.INDIVIDUAL,
                "desc",
                Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void listReturnsPortfolios() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(portfolioService.listForUser(userId))
                .thenReturn(List.of(sample(pid, "Main"), sample(UUID.randomUUID(), "BES")));

        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Main"));
    }

    @Test
    void getReturnsSinglePortfolio() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(portfolioService.getForUser(eq(userId), eq(pid))).thenReturn(sample(pid, "Main"));

        mockMvc.perform(get("/api/v1/portfolios/" + pid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pid.toString()))
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"));
    }

    @Test
    void get404WhenNotFound() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(portfolioService.getForUser(eq(userId), eq(pid)))
                .thenThrow(new ResourceNotFoundException("Portfolio not found"));

        mockMvc.perform(get("/api/v1/portfolios/" + pid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void createReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(portfolioService.create(eq(userId), any(CreatePortfolioRequest.class)))
                .thenReturn(sample(pid, "New"));

        mockMvc.perform(
                        post("/api/v1/portfolios")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"New\",\"type\":\"INDIVIDUAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/portfolios")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"type\":\"INDIVIDUAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsMissingType() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/portfolios")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Main\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateReturnsUpdatedBody() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(portfolioService.update(eq(userId), eq(pid), any(UpdatePortfolioRequest.class)))
                .thenReturn(sample(pid, "Renamed"));

        mockMvc.perform(
                        put("/api/v1/portfolios/" + pid)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void updateRejectsBlankName() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        put("/api/v1/portfolios/" + UUID.randomUUID())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/portfolios/" + pid).with(csrf()))
                .andExpect(status().isNoContent());

        verify(portfolioService).delete(eq(userId), eq(pid));
    }

    @Test
    void malformedIdReturnsBadRequest() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/portfolios/not-a-uuid"))
                .andExpect(status().is5xxServerError());
    }
}
