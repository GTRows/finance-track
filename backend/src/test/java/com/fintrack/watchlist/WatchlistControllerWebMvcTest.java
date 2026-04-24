package com.fintrack.watchlist;

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
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.watchlist.dto.AddWatchlistRequest;
import com.fintrack.watchlist.dto.WatchlistEntryResponse;
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

@WebMvcTest(controllers = WatchlistController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class WatchlistControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean WatchlistService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void listReturnsEntries() throws Exception {
        stubAuthenticatedUser();
        UUID aid = UUID.randomUUID();
        when(service.list(eq(userId)))
                .thenReturn(List.of(new WatchlistEntryResponse(aid, "note", Instant.now())));

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assetId").value(aid.toString()));
    }

    @Test
    void addReturnsEntry() throws Exception {
        stubAuthenticatedUser();
        UUID aid = UUID.randomUUID();
        when(service.add(eq(userId), any(AddWatchlistRequest.class)))
                .thenReturn(new WatchlistEntryResponse(aid, "note", Instant.now()));

        mockMvc.perform(
                        post("/api/v1/watchlist")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"assetId\":\"" + aid + "\",\"note\":\"note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(aid.toString()));
    }

    @Test
    void addRejectsMissingAssetId() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/watchlist")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void removeReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID aid = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/watchlist/" + aid).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).remove(eq(userId), eq(aid));
    }
}
