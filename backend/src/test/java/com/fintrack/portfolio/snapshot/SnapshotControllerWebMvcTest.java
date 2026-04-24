package com.fintrack.portfolio.snapshot;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.portfolio.snapshot.dto.SnapshotResponse;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SnapshotController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SnapshotControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean SnapshotService snapshotService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void listReturnsHistory() throws Exception {
        stubAuthenticatedUser();
        UUID pid = UUID.randomUUID();
        when(snapshotService.listForPortfolio(eq(userId), eq(pid)))
                .thenReturn(
                        List.of(
                                new SnapshotResponse(
                                        LocalDate.of(2026, 4, 1),
                                        new BigDecimal("100"),
                                        new BigDecimal("80"),
                                        new BigDecimal("20"),
                                        new BigDecimal("0.25"))));

        mockMvc.perform(get("/api/v1/portfolios/" + pid + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-04-01"))
                .andExpect(jsonPath("$[0].totalValueTry").value(100));
    }
}
