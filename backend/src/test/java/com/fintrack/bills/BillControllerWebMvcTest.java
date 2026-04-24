package com.fintrack.bills;

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
import com.fintrack.bills.dto.BillResponse;
import com.fintrack.bills.dto.CreateBillRequest;
import com.fintrack.bills.dto.PayBillRequest;
import com.fintrack.bills.dto.SubscriptionAuditDto;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.common.exception.ResourceNotFoundException;
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

@WebMvcTest(controllers = BillController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BillControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean BillService billService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private BillResponse sample(UUID id, String name) {
        return new BillResponse(
                id,
                name,
                new BigDecimal("150.00"),
                "TRY",
                "utilities",
                15,
                true,
                false,
                3,
                null,
                "PENDING",
                null,
                5,
                null,
                null,
                null);
    }

    @Test
    void listReturnsBills() throws Exception {
        stubAuthenticatedUser();
        when(billService.listForUser(userId))
                .thenReturn(List.of(sample(UUID.randomUUID(), "Internet")));

        mockMvc.perform(get("/api/v1/bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Internet"));
    }

    @Test
    void createReturns201() throws Exception {
        stubAuthenticatedUser();
        UUID bid = UUID.randomUUID();
        when(billService.create(eq(userId), any(CreateBillRequest.class)))
                .thenReturn(sample(bid, "Internet"));

        mockMvc.perform(
                        post("/api/v1/bills")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Internet\",\"amount\":150.00,\"dueDay\":15,\"remindDaysBefore\":3,\"autoPay\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bid.toString()));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/bills")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"\",\"amount\":150.00,\"dueDay\":15,\"remindDaysBefore\":3,\"autoPay\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsNonPositiveAmount() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/bills")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Rent\",\"amount\":0,\"dueDay\":1,\"remindDaysBefore\":3,\"autoPay\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsInvalidDueDay() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/bills")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Rent\",\"amount\":500,\"dueDay\":32,\"remindDaysBefore\":3,\"autoPay\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID bid = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/bills/" + bid).with(csrf()))
                .andExpect(status().isNoContent());

        verify(billService).delete(eq(userId), eq(bid));
    }

    @Test
    void delete404WhenNotOwned() throws Exception {
        stubAuthenticatedUser();
        UUID bid = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Bill not found"))
                .when(billService)
                .delete(eq(userId), eq(bid));

        mockMvc.perform(delete("/api/v1/bills/" + bid).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void payReturnsUpdatedBill() throws Exception {
        stubAuthenticatedUser();
        UUID bid = UUID.randomUUID();
        when(billService.pay(eq(userId), eq(bid), any(PayBillRequest.class)))
                .thenReturn(sample(bid, "Internet"));

        mockMvc.perform(
                        post("/api/v1/bills/" + bid + "/pay")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"period\":\"2026-04\",\"amount\":150}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bid.toString()));
    }

    @Test
    void payRejectsBlankPeriod() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/bills/" + UUID.randomUUID() + "/pay")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"period\":\"\",\"amount\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void historyReturnsList() throws Exception {
        stubAuthenticatedUser();
        UUID bid = UUID.randomUUID();
        when(billService.history(eq(userId), eq(bid))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bills/" + bid + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void markUsedReturnsBill() throws Exception {
        stubAuthenticatedUser();
        UUID bid = UUID.randomUUID();
        when(billService.markUsed(eq(userId), eq(bid))).thenReturn(sample(bid, "Gym"));

        mockMvc.perform(post("/api/v1/bills/" + bid + "/mark-used").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bid.toString()));
    }

    @Test
    void auditReturnsDto() throws Exception {
        stubAuthenticatedUser();
        when(billService.audit(eq(userId)))
                .thenReturn(
                        new SubscriptionAuditDto(
                                new BigDecimal("500"), BigDecimal.ZERO, 0, List.of()));

        mockMvc.perform(get("/api/v1/bills/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMonthlySpend").value(500));
    }
}
