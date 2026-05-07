package com.fintrack.account;

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

import com.fintrack.account.dto.AccountResponse;
import com.fintrack.account.dto.AccountTotalsResponse;
import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.dto.UpdateAccountRequest;
import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.entity.Account;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.common.exception.ResourceNotFoundException;
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

@WebMvcTest(controllers = AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AccountControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean AccountService accountService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private AccountResponse sample(UUID id, String name, String currency) {
        return new AccountResponse(
                id,
                name,
                Account.AccountType.BANK_CHECKING,
                currency,
                "Akbank",
                "1234",
                "note",
                new BigDecimal("100.00"),
                false,
                Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void listReturnsAccounts() throws Exception {
        stubAuthenticatedUser();
        UUID a = UUID.randomUUID();
        when(accountService.listForUser(userId))
                .thenReturn(
                        List.of(sample(a, "Main", "TRY"), sample(UUID.randomUUID(), "USD", "USD")));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Main"))
                .andExpect(jsonPath("$[0].currency").value("TRY"));
    }

    @Test
    void getReturnsSingleAccount() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(accountService.getForUser(eq(userId), eq(id))).thenReturn(sample(id, "Main", "TRY"));

        mockMvc.perform(get("/api/v1/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("BANK_CHECKING"))
                .andExpect(jsonPath("$.currency").value("TRY"));
    }

    @Test
    void get404WhenNotFound() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(accountService.getForUser(eq(userId), eq(id)))
                .thenThrow(new ResourceNotFoundException("Account not found"));

        mockMvc.perform(get("/api/v1/accounts/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void createReturns201WithBody() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(accountService.create(eq(userId), any(CreateAccountRequest.class)))
                .thenReturn(sample(id, "New", "TRY"));

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"New\",\"type\":\"BANK_CHECKING\",\"currency\":\"TRY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void create400OnMissingName() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create400OnInvalidCurrency() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Main\",\"type\":\"BANK_CHECKING\",\"currency\":\"tr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create400OnDuplicateName() throws Exception {
        stubAuthenticatedUser();
        when(accountService.create(eq(userId), any(CreateAccountRequest.class)))
                .thenThrow(
                        new BusinessRuleException(
                                "Account name already exists", "ACCOUNT_NAME_DUPLICATE"));

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Main\",\"type\":\"BANK_CHECKING\",\"currency\":\"TRY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NAME_DUPLICATE"));
    }

    @Test
    void updateReturnsUpdated() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(accountService.update(eq(userId), eq(id), any(UpdateAccountRequest.class)))
                .thenReturn(sample(id, "Renamed", "TRY"));

        mockMvc.perform(
                        put("/api/v1/accounts/" + id)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Renamed\",\"currency\":\"TRY\",\"currentBalance\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void delete204OnSuccess() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/accounts/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(accountService).delete(eq(userId), eq(id));
    }

    @Test
    void totalsReturnsAggregateResponse() throws Exception {
        stubAuthenticatedUser();
        when(accountService.totalsForUser(userId))
                .thenReturn(
                        new AccountTotalsResponse(
                                3,
                                1,
                                List.of(
                                        new AccountTotalsResponse.CurrencyTotal(
                                                "TRY", new BigDecimal("1000")),
                                        new AccountTotalsResponse.CurrencyTotal(
                                                "USD", new BigDecimal("250")))));

        mockMvc.perform(get("/api/v1/accounts/totals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveCount").value(3))
                .andExpect(jsonPath("$.archivedCount").value(1))
                .andExpect(jsonPath("$.byCurrency.length()").value(2))
                .andExpect(jsonPath("$.byCurrency[0].currency").value("TRY"));
    }
}
