package com.fintrack.budget.receipt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
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

@WebMvcTest(controllers = ReceiptController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReceiptControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean ReceiptStorageService storage;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void uploadReturnsStoredReceipt() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(storage.store(eq(userId), eq(id), any(MultipartFile.class)))
                .thenReturn(new ReceiptStorageService.StoredReceipt("u/x.png", "image/png", 12L));

        MockMultipartFile file =
                new MockMultipartFile("file", "x.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(
                        multipart("/api/v1/budget/transactions/" + id + "/receipt")
                                .file(file)
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relativePath").value("u/x.png"))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.bytes").value(12));
    }

    @Test
    void downloadReturnsBytes() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        byte[] body = new byte[] {0x01, 0x02, 0x03};
        when(storage.load(eq(userId), eq(id)))
                .thenReturn(new ReceiptStorageService.Loaded(body, "image/png"));

        mockMvc.perform(get("/api/v1/budget/transactions/" + id + "/receipt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(body));
    }

    @Test
    void deleteReturns204() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/budget/transactions/" + id + "/receipt").with(csrf()))
                .andExpect(status().isNoContent());

        verify(storage).delete(eq(userId), eq(id));
    }
}
