package com.fintrack.bills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.entity.User;
import com.fintrack.common.event.BillPaidEvent;
import com.fintrack.notification.MailService;
import com.fintrack.push.PushService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillPaidNotificationListenerTest {

    @Mock UserRepository userRepository;
    @Mock MailService mailService;
    @Mock PushService pushService;

    @InjectMocks BillPaidNotificationListener listener;

    private final UUID userId = UUID.randomUUID();
    private final UUID billId = UUID.randomUUID();

    private User verifiedUser() {
        return User.builder()
                .id(userId)
                .username("alice")
                .email("alice@example.com")
                .password("x")
                .emailVerified(true)
                .build();
    }

    private User unverifiedUser() {
        return User.builder()
                .id(userId)
                .username("alice")
                .email("alice@example.com")
                .password("x")
                .emailVerified(false)
                .build();
    }

    private BillPaidEvent event() {
        return new BillPaidEvent(
                userId, billId, "Electric", "2026-04", new BigDecimal("350"), "TRY", Instant.now());
    }

    @Test
    void verifiedUserGetsMailAndPush() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(verifiedUser()));

        listener.onBillPaid(event());

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendHtml(eq("alice@example.com"), subject.capture(), body.capture());
        assertThat(subject.getValue()).contains("Electric");
        assertThat(body.getValue()).contains("350");
        verify(pushService).sendToUser(userId);
    }

    @Test
    void unverifiedUserSkipsBothMailAndPush() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(unverifiedUser()));

        listener.onBillPaid(event());

        verify(mailService, never()).sendHtml(anyString(), anyString(), anyString());
        verify(pushService, never()).sendToUser(any());
    }

    @Test
    void missingUserSkipsBothMailAndPush() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        listener.onBillPaid(event());

        verify(mailService, never()).sendHtml(anyString(), anyString(), anyString());
        verify(pushService, never()).sendToUser(any());
    }

    @Test
    void pushFailureDoesNotPropagate() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(verifiedUser()));
        doThrow(new RuntimeException("push down")).when(pushService).sendToUser(userId);

        listener.onBillPaid(event());

        verify(mailService).sendHtml(eq("alice@example.com"), anyString(), anyString());
    }
}
