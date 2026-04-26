package com.fintrack.budget.allocation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.budget.allocation.AllocationDtos.BucketResponse;
import com.fintrack.budget.allocation.AllocationDtos.PreviewRequest;
import com.fintrack.budget.allocation.AllocationDtos.PreviewResponse;
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

@WebMvcTest(controllers = AllocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AllocationControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean CashFlowAllocatorService service;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void listReturnsBuckets() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.listBuckets(eq(userId)))
                .thenReturn(
                        List.of(new BucketResponse(id, "Needs", new BigDecimal("50"), null, 0)));

        mockMvc.perform(get("/api/v1/budget/allocation/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Needs"));
    }

    @Test
    void replaceUpdatesBuckets() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(service.replaceBuckets(eq(userId), any()))
                .thenReturn(
                        List.of(new BucketResponse(id, "Wants", new BigDecimal("30"), null, 0)));

        mockMvc.perform(
                        put("/api/v1/budget/allocation/buckets")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"buckets\":[{\"name\":\"Wants\",\"percent\":30}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Wants"));
    }

    @Test
    void replaceRejectsMissingBuckets() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        put("/api/v1/budget/allocation/buckets")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void replaceRejectsBucketWithBlankName() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        put("/api/v1/budget/allocation/buckets")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"buckets\":[{\"name\":\"\",\"percent\":30}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void previewReturnsAllocation() throws Exception {
        stubAuthenticatedUser();
        when(service.preview(eq(userId), any(PreviewRequest.class)))
                .thenReturn(
                        new PreviewResponse(
                                new BigDecimal("10000"),
                                new BigDecimal("2000"),
                                new BigDecimal("8000"),
                                new BigDecimal("8000"),
                                BigDecimal.ZERO,
                                List.of()));

        mockMvc.perform(
                        post("/api/v1/budget/allocation/preview")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"income\":10000,\"obligations\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discretionary").value(8000));
    }

    @Test
    void previewRejectsNegativeIncome() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/budget/allocation/preview")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"income\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
