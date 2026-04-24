package com.fintrack.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fintrack.alert.dto.NotificationResponse;
import com.fintrack.common.entity.AlertNotification;
import com.fintrack.common.entity.AlertNotification.SourceType;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock AlertNotificationRepository notificationRepo;

    @InjectMocks NotificationService service;

    private final UUID userId = UUID.randomUUID();

    private AlertNotification notification(String message, Instant readAt) {
        return AlertNotification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .sourceType(SourceType.PRICE_ALERT)
                .message(message)
                .readAt(readAt)
                .build();
    }

    @Test
    void listReturnsMappedRows() {
        AlertNotification n = notification("hello", null);
        when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n));

        List<NotificationResponse> res = service.list(userId);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).message()).isEqualTo("hello");
        assertThat(res.get(0).readAt()).isNull();
    }

    @Test
    void listReturnsEmptyWhenNoNotifications() {
        when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        assertThat(service.list(userId)).isEmpty();
    }

    @Test
    void unreadCountDelegatesToRepository() {
        when(notificationRepo.countByUserIdAndReadAtIsNull(userId)).thenReturn(5L);

        assertThat(service.unreadCount(userId)).isEqualTo(5L);
    }

    @Test
    void markAsReadThrowsWhenNotOwned() {
        UUID id = UUID.randomUUID();
        when(notificationRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAsReadSetsReadAtWhenUnread() {
        AlertNotification n = notification("x", null);
        when(notificationRepo.findByIdAndUserId(n.getId(), userId)).thenReturn(Optional.of(n));

        Instant before = Instant.now();
        NotificationResponse res = service.markAsRead(userId, n.getId());

        assertThat(n.getReadAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(res.readAt()).isNotNull();
    }

    @Test
    void markAsReadPreservesExistingReadAtWhenAlreadyRead() {
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        AlertNotification n = notification("x", original);
        when(notificationRepo.findByIdAndUserId(n.getId(), userId)).thenReturn(Optional.of(n));

        service.markAsRead(userId, n.getId());

        assertThat(n.getReadAt()).isEqualTo(original);
    }

    @Test
    void markAllAsReadOnlyStampsPreviouslyUnreadNotifications() {
        Instant existing = Instant.parse("2026-01-01T00:00:00Z");
        AlertNotification read = notification("read", existing);
        AlertNotification unread1 = notification("u1", null);
        AlertNotification unread2 = notification("u2", null);
        when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(read, unread1, unread2));

        service.markAllAsRead(userId);

        assertThat(read.getReadAt()).isEqualTo(existing);
        assertThat(unread1.getReadAt()).isNotNull();
        assertThat(unread2.getReadAt()).isNotNull();
    }

    @Test
    void markAllAsReadIsNoOpWhenNoUnread() {
        Instant existing = Instant.parse("2026-01-01T00:00:00Z");
        AlertNotification read = notification("read", existing);
        when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(read));

        service.markAllAsRead(userId);

        assertThat(read.getReadAt()).isEqualTo(existing);
    }
}
