package com.fintrack.alert.dto;

import com.fintrack.common.entity.AlertNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID alertId,
        UUID assetId,
        String message,
        Instant readAt,
        Instant createdAt
) {

    public static NotificationResponse from(AlertNotification n) {
        return new NotificationResponse(
                n.getId(),
                n.getAlertId(),
                n.getAssetId(),
                n.getMessage(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
