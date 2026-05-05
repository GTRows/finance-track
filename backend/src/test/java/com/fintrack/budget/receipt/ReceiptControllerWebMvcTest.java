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
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.GlobalExceptionHandler;
import com.fintrack.notification.MailProperties;
import java.time.Duration;
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
    @MockBean ReceiptUrlSigner signer;
    @MockBean ReceiptSigningProperties signingProperties;
    @MockBean MailProperties mailProperties;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    // --- existing upload / download / delete tests ---

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

    // --- Task 2 new cases ---

    @Test
    void getSignedUrlReturnsUrlAndExpiresAt() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        when(signingProperties.tokenTtl()).thenReturn(Duration.ofMinutes(5));
        when(mailProperties.getBaseUrl()).thenReturn("http://localhost");
        when(signer.sign(eq(userId), eq(id))).thenReturn("signed-token");

        mockMvc.perform(get("/api/v1/budget/transactions/" + id + "/receipt/url"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.url")
                                .value(
                                        "http://localhost/api/v1/budget/transactions/"
                                                + id
                                                + "/receipt?token=signed-token"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void downloadWithValidTokenReturnsBytes() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tokenUserId = UUID.randomUUID();
        byte[] body = new byte[] {0x0A, 0x0B};
        when(signer.verifyAndExtractUserId(eq(id), eq("valid-token"))).thenReturn(tokenUserId);
        when(storage.load(eq(tokenUserId), eq(id)))
                .thenReturn(new ReceiptStorageService.Loaded(body, "image/jpeg"));

        mockMvc.perform(
                        get("/api/v1/budget/transactions/" + id + "/receipt")
                                .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes(body));
    }

    @Test
    void downloadWithTamperedTokenReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(signer.verifyAndExtractUserId(eq(id), eq("tampered")))
                .thenThrow(
                        new BusinessRuleException("Invalid signed URL", "RECEIPT_TOKEN_INVALID"));

        mockMvc.perform(
                        get("/api/v1/budget/transactions/" + id + "/receipt")
                                .param("token", "tampered"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIPT_TOKEN_INVALID"));
    }

    @Test
    void downloadWithExpiredTokenReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(signer.verifyAndExtractUserId(eq(id), eq("expired-token")))
                .thenThrow(
                        new BusinessRuleException("Invalid signed URL", "RECEIPT_TOKEN_INVALID"));

        mockMvc.perform(
                        get("/api/v1/budget/transactions/" + id + "/receipt")
                                .param("token", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIPT_TOKEN_INVALID"));
    }

    @Test
    void downloadWithoutAuthAndWithoutTokenReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        // no stubAuthenticatedUser() — user principal is null, no token param
        mockMvc.perform(get("/api/v1/budget/transactions/" + id + "/receipt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIPT_TOKEN_INVALID"));
    }

    @Test
    void downloadWithJwtAndNoTokenReturnsBytes() throws Exception {
        stubAuthenticatedUser();
        UUID id = UUID.randomUUID();
        byte[] body = new byte[] {0x05, 0x06};
        when(storage.load(eq(userId), eq(id)))
                .thenReturn(new ReceiptStorageService.Loaded(body, "image/png"));

        mockMvc.perform(get("/api/v1/budget/transactions/" + id + "/receipt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(body));
    }
}
