package com.fintrack.price;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.websocket.PriceBroadcaster;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PriceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PriceControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean PriceSyncService priceSyncService;
    @MockBean PriceBroadcaster priceBroadcaster;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void refreshAllReturnsResultAndBroadcasts() throws Exception {
        stubAuthenticatedUser();
        when(priceSyncService.refreshAll())
                .thenReturn(
                        new PriceSyncService.SyncResult(
                                3, 4, 5, 6, 7, Instant.parse("2026-04-01T10:00:00Z")));

        mockMvc.perform(post("/api/v1/prices/refresh").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cryptoUpdated").value(3))
                .andExpect(jsonPath("$.fundUpdated").value(5));

        verify(priceBroadcaster).broadcastAll();
    }

    @Test
    void refreshOneBroadcastsWhenUpdated() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(priceSyncService.refreshAsset(eq(id))).thenReturn(true);

        mockMvc.perform(post("/api/v1/prices/refresh/" + id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(true));

        verify(priceBroadcaster).broadcastAll();
    }

    @Test
    void refreshOneSkipsBroadcastWhenNotUpdated() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(priceSyncService.refreshAsset(eq(id))).thenReturn(false);

        mockMvc.perform(post("/api/v1/prices/refresh/" + id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(false));

        org.mockito.Mockito.verifyNoInteractions(priceBroadcaster);
    }
}
