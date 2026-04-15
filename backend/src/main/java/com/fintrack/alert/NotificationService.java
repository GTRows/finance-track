package com.fintrack.alert;

import com.fintrack.alert.dto.NotificationResponse;
import com.fintrack.common.entity.AlertNotification;
import com.fintrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AlertNotificationRepository notificationRepo;

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID userId) {
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        AlertNotification n = notificationRepo.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        Instant now = Instant.now();
        List<AlertNotification> unread = notificationRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .toList();
        for (AlertNotification n : unread) {
            n.setReadAt(now);
        }
    }
}
