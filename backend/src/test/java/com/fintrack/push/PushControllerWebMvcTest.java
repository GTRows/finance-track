package com.fintrack.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.fintrack.common.entity.PushSubscription;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PushController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PushControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean PushService pushService;
    @MockBean VapidKeyManager vapid;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void vapidPublicKeyReturnsKey() throws Exception {
        stubAuthenticatedUser();
        when(vapid.getPublicKeyB64Url()).thenReturn("BPubKey");

        mockMvc.perform(get("/api/v1/push/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("BPubKey"));
    }

    @Test
    void subscribeReturnsId() throws Exception {
        stubAuthenticatedUser();
        UUID subId = UUID.randomUUID();
        PushSubscription saved = PushSubscription.builder().id(subId).build();
        when(pushService.subscribe(eq(userId), anyString(), anyString(), anyString(), any()))
                .thenReturn(saved);

        mockMvc.perform(
                        post("/api/v1/push/subscribe")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"endpoint\":\"https://push.example/x\",\"p256dh\":\"abc\",\"auth\":\"def\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subId.toString()))
                .andExpect(jsonPath("$.subscribed").value(true));
    }

    @Test
    void subscribeRejectsBlankEndpoint() throws Exception {
        stubAuthenticatedUser();
        mockMvc.perform(
                        post("/api/v1/push/subscribe")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"endpoint\":\"\",\"p256dh\":\"abc\",\"auth\":\"def\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unsubscribeReturns204() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(
                        delete("/api/v1/push/subscribe")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"endpoint\":\"https://push.example/x\"}"))
                .andExpect(status().isNoContent());

        verify(pushService).unsubscribe(eq(userId), eq("https://push.example/x"));
    }

    @Test
    void testReturnsDeliveredCount() throws Exception {
        stubAuthenticatedUser();
        when(pushService.sendToUser(eq(userId))).thenReturn(2);

        mockMvc.perform(post("/api/v1/push/test").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(2));
    }
}
