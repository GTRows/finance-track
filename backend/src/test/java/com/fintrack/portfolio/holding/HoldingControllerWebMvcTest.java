package com.fintrack.portfolio.holding;

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
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.holding.dto.AddHoldingRequest;
import com.fintrack.portfolio.holding.dto.HoldingResponse;
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

@WebMvcTest(controllers = HoldingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class HoldingControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean HoldingService holdingService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private HoldingResponse sample(UUID id, UUID portfolioId) {
        return new HoldingResponse(
                id,
                portfolioId,
                UUID.randomUUID(),
                "BTC",
                "Bitcoin",
                AssetType.CRYPTO,
                new BigDecimal("1"),
                new BigDecimal("80"),
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("80"),
                new BigDecimal("20"),
                new BigDecimal("25"),
                false,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void listReturnsHoldings() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(holdingService.listForPortfolio(eq(userId), eq(pid)))
                .thenReturn(List.of(sample(UUID.randomUUID(), pid)));

        mockMvc.perform(get("/api/v1/portfolios/" + pid + "/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].assetSymbol").value("BTC"));
    }

    @Test
    void addReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID hid = UUID.randomUUID();
        when(holdingService.add(eq(userId), eq(pid), any(AddHoldingRequest.class)))
                .thenReturn(sample(hid, pid));

        mockMvc.perform(
                        post("/api/v1/portfolios/" + pid + "/holdings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + UUID.randomUUID()
                                                + "\",\"quantity\":1,\"avgCostTry\":80}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(hid.toString()));
    }

    @Test
    void addRejectsMissingAssetId() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/portfolios/" + UUID.randomUUID() + "/holdings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":1,\"avgCostTry\":80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addRejectsZeroQuantity() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/portfolios/" + UUID.randomUUID() + "/holdings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + UUID.randomUUID()
                                                + "\",\"quantity\":0,\"avgCostTry\":80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addRejectsNegativeAvgCost() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/portfolios/" + UUID.randomUUID() + "/holdings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + UUID.randomUUID()
                                                + "\",\"quantity\":1,\"avgCostTry\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID hid = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/portfolios/" + pid + "/holdings/" + hid).with(csrf()))
                .andExpect(status().isNoContent());

        verify(holdingService).delete(eq(userId), eq(pid), eq(hid));
    }

    @Test
    void delete404WhenNotFound() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID hid = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Holding not found"))
                .when(holdingService)
                .delete(eq(userId), eq(pid), eq(hid));

        mockMvc.perform(delete("/api/v1/portfolios/" + pid + "/holdings/" + hid).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void togglePinReturnsUpdatedHolding() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        UUID hid = UUID.randomUUID();
        when(holdingService.togglePin(eq(userId), eq(pid), eq(hid))).thenReturn(sample(hid, pid));

        mockMvc.perform(put("/api/v1/portfolios/" + pid + "/holdings/" + hid + "/pin").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hid.toString()));
    }
}
