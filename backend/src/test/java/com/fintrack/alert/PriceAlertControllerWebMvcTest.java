package com.fintrack.alert;

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

import com.fintrack.alert.dto.AlertResponse;
import com.fintrack.alert.dto.CreateAlertRequest;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.PriceAlert;
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

@WebMvcTest(controllers = PriceAlertController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PriceAlertControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean PriceAlertService alertService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private AlertResponse sample(UUID id) {
        return new AlertResponse(
                id,
                UUID.randomUUID(),
                "BTC",
                "Bitcoin",
                AssetType.CRYPTO,
                new BigDecimal("100"),
                PriceAlert.Direction.ABOVE,
                new BigDecimal("120"),
                PriceAlert.Status.ACTIVE,
                null,
                null);
    }

    @Test
    void listReturnsAlerts() throws Exception {
        stubAuthenticatedUser();
        UUID aid = UUID.randomUUID();
        when(alertService.listForUser(eq(userId))).thenReturn(List.of(sample(aid)));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(aid.toString()));
    }

    @Test
    void createReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID aid = UUID.randomUUID();
        when(alertService.create(eq(userId), any(CreateAlertRequest.class)))
                .thenReturn(sample(aid));

        mockMvc.perform(
                        post("/api/v1/alerts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + UUID.randomUUID()
                                                + "\",\"direction\":\"ABOVE\",\"thresholdTry\":120}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(aid.toString()));
    }

    @Test
    void createRejectsMissingAssetId() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/alerts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"direction\":\"ABOVE\",\"thresholdTry\":120}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsNonPositiveThreshold() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/alerts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"assetId\":\""
                                                + UUID.randomUUID()
                                                + "\",\"direction\":\"ABOVE\",\"thresholdTry\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID aid = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/alerts/" + aid).with(csrf()))
                .andExpect(status().isNoContent());

        verify(alertService).delete(eq(userId), eq(aid));
    }

    @Test
    void disableReturnsAlert() throws Exception {
        stubAuthenticatedUser();
        UUID aid = UUID.randomUUID();
        when(alertService.disable(eq(userId), eq(aid))).thenReturn(sample(aid));

        mockMvc.perform(post("/api/v1/alerts/" + aid + "/disable").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aid.toString()));
    }
}
