package com.fintrack.imports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.imports.dto.ImportSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(controllers = ImportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ImportControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean ExcelImportService importService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void previewReturnsSummary() throws Exception {
        stubAuthenticatedUser();
        when(importService.preview(any(MultipartFile.class)))
                .thenReturn(new ImportSummary(10, 9, 1, 0, List.of()));

        MockMultipartFile file =
                new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[] {1});

        mockMvc.perform(multipart("/api/v1/import/excel/preview").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(10))
                .andExpect(jsonPath("$.importedRows").value(9));
    }

    @Test
    void commitReturnsSummary() throws Exception {
        stubAuthenticatedUser();
        when(importService.commit(eq(userId), any(MultipartFile.class)))
                .thenReturn(new ImportSummary(5, 5, 0, 0, List.of()));

        MockMultipartFile file =
                new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[] {1});

        mockMvc.perform(multipart("/api/v1/import/excel/commit").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(5));
    }
}
