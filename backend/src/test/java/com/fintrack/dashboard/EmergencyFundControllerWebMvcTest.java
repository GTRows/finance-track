package com.fintrack.dashboard;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.entity.Account;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.dashboard.dto.EmergencyFundResponse;
import com.fintrack.settings.SettingsService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EmergencyFundController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmergencyFundControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean EmergencyFundService emergencyFundService;
    @MockBean SettingsService settingsService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private EmergencyFundResponse sampleResponse() {
        return new EmergencyFundResponse(
                new BigDecimal("9000"),
                List.of(new EmergencyFundResponse.CurrencyBucket("TRY", new BigDecimal("9000"))),
                new BigDecimal("1500"),
                new BigDecimal("6.0"),
                "amber",
                List.of(Account.AccountType.BANK_SAVINGS),
                12,
                6,
                3);
    }

    @Test
    void getReturnsResponse() throws Exception {
        stubAuthenticatedUser();
        when(emergencyFundService.compute(eq(userId))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/dashboard/emergency-fund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentReserve").value(9000))
                .andExpect(jsonPath("$.status").value("amber"));
    }

    @Test
    void updateTypesReturnsRefreshedResponse() throws Exception {
        stubAuthenticatedUser();
        when(emergencyFundService.compute(eq(userId))).thenReturn(sampleResponse());

        mockMvc.perform(
                        put("/api/v1/dashboard/emergency-fund/types")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"types\":[\"BANK_SAVINGS\",\"CASH\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includedTypes[0]").value("BANK_SAVINGS"));

        verify(settingsService)
                .updateEmergencyFundTypes(
                        eq(userId),
                        eq(List.of(Account.AccountType.BANK_SAVINGS, Account.AccountType.CASH)));
    }

    @Test
    void updateTypes400OnEmptyList() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        put("/api/v1/dashboard/emergency-fund/types")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"types\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateTypes400OnNullTypesField() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        put("/api/v1/dashboard/emergency-fund/types")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
