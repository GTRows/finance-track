package com.fintrack.imports.bank;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.imports.bank.dto.BankCsvImportSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(controllers = BankCsvImportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BankCsvImportControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean BankCsvImportService importService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    private MockMultipartFile csvFile() {
        return new MockMultipartFile(
                "file", "g.csv", "text/csv", "Tarih;Aciklama;Tutar\n".getBytes());
    }

    @Test
    void preview_returns200_withSummaryJson() throws Exception {
        stubAuthenticatedUser();
        UUID accountId = UUID.randomUUID();
        when(importService.preview(
                        any(MultipartFile.class), eq(Bank.GARANTI), eq(accountId), eq(userId)))
                .thenReturn(new BankCsvImportSummary(2, 0, 0, 0, 0, List.of()));

        mockMvc.perform(
                        multipart("/api/v1/import/bank-csv/preview")
                                .file(csvFile())
                                .param("bank", "GARANTI")
                                .param("accountId", accountId.toString())
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.importedRows").value(0));
    }

    @Test
    void commit_returns200_withSummaryJson() throws Exception {
        stubAuthenticatedUser();
        UUID accountId = UUID.randomUUID();
        when(importService.commit(
                        any(MultipartFile.class), eq(Bank.AKBANK), eq(accountId), eq(userId)))
                .thenReturn(new BankCsvImportSummary(5, 4, 1, 0, 0, List.of()));

        mockMvc.perform(
                        multipart("/api/v1/import/bank-csv/commit")
                                .file(csvFile())
                                .param("bank", "AKBANK")
                                .param("accountId", accountId.toString())
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(5))
                .andExpect(jsonPath("$.importedRows").value(4));
    }

    @Test
    void preview_returns400_onUnknownBank() throws Exception {
        stubAuthenticatedUser();
        UUID accountId = UUID.randomUUID();
        mockMvc.perform(
                        multipart("/api/v1/import/bank-csv/preview")
                                .file(csvFile())
                                .param("bank", "XYZ")
                                .param("accountId", accountId.toString())
                                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_returns400_onMissingAccount() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        multipart("/api/v1/import/bank-csv/preview")
                                .file(csvFile())
                                .param("bank", "GARANTI")
                                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_returns400_onMissingFile() throws Exception {
        stubAuthenticatedUser();
        UUID accountId = UUID.randomUUID();
        mockMvc.perform(
                        multipart("/api/v1/import/bank-csv/preview")
                                .param("bank", "GARANTI")
                                .param("accountId", accountId.toString())
                                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commit_routesToCommitMethod() throws Exception {
        stubAuthenticatedUser();
        UUID accountId = UUID.randomUUID();
        when(importService.commit(
                        any(MultipartFile.class), eq(Bank.ISBANK), eq(accountId), eq(userId)))
                .thenReturn(new BankCsvImportSummary(0, 0, 0, 0, 0, List.of()));

        mockMvc.perform(
                        multipart("/api/v1/import/bank-csv/commit")
                                .file(csvFile())
                                .param("bank", "ISBANK")
                                .param("accountId", accountId.toString())
                                .with(csrf()))
                .andExpect(status().isOk());

        verify(importService)
                .commit(any(MultipartFile.class), eq(Bank.ISBANK), eq(accountId), eq(userId));
    }

    @Test
    void commit_returns400_onBusinessRuleFromService() throws Exception {
        stubAuthenticatedUser();
        UUID accountId = UUID.randomUUID();
        when(importService.commit(
                        any(MultipartFile.class), eq(Bank.GARANTI), eq(accountId), eq(userId)))
                .thenThrow(new BusinessRuleException("Account not found", "ACCOUNT_NOT_OWNED"));

        mockMvc.perform(
                        multipart("/api/v1/import/bank-csv/commit")
                                .file(csvFile())
                                .param("bank", "GARANTI")
                                .param("accountId", accountId.toString())
                                .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
