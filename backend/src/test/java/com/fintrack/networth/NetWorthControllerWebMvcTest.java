package com.fintrack.networth;

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
import com.fintrack.networth.dto.NetWorthEventResponse;
import com.fintrack.networth.dto.NetWorthTimelineResponse;
import com.fintrack.networth.dto.UpsertEventRequest;
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

@WebMvcTest(controllers = NetWorthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NetWorthControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean NetWorthService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private NetWorthEventResponse sampleEvent(UUID id) {
        return new NetWorthEventResponse(
                id,
                LocalDate.of(2026, 4, 1),
                "DEPOSIT",
                "Bonus",
                "Annual bonus",
                new BigDecimal("5000"));
    }

    @Test
    void timelineReturnsSeriesAndEvents() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.timeline(eq(userId)))
                .thenReturn(
                        new NetWorthTimelineResponse(
                                List.of(
                                        new NetWorthTimelineResponse.Point(
                                                LocalDate.of(2026, 4, 1),
                                                new BigDecimal("100000"),
                                                new BigDecimal("80000"))),
                                List.of(sampleEvent(id))));

        mockMvc.perform(get("/api/v1/net-worth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[0].totalValueTry").value(100000))
                .andExpect(jsonPath("$.events[0].id").value(id.toString()));
    }

    @Test
    void listEventsReturnsRows() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.listEvents(eq(userId))).thenReturn(List.of(sampleEvent(id)));

        mockMvc.perform(get("/api/v1/net-worth/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].label").value("Bonus"));
    }

    @Test
    void createReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.create(eq(userId), any(UpsertEventRequest.class))).thenReturn(sampleEvent(id));

        mockMvc.perform(
                        post("/api/v1/net-worth/events")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"eventDate\":\"2026-04-01\",\"eventType\":\"DEPOSIT\",\"label\":\"Bonus\",\"impactTry\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createRejectsBlankLabel() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/net-worth/events")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"eventDate\":\"2026-04-01\",\"eventType\":\"DEPOSIT\",\"label\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateReturnsEvent() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.update(eq(userId), eq(id), any(UpsertEventRequest.class)))
                .thenReturn(sampleEvent(id));

        mockMvc.perform(
                        put("/api/v1/net-worth/events/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"eventDate\":\"2026-04-01\",\"eventType\":\"DEPOSIT\",\"label\":\"Bonus\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/net-worth/events/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).delete(eq(userId), eq(id));
    }
}
