package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/** In-app notification emitted when a price alert triggers. */
@Entity
@Table(name = "alert_notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private SourceType sourceType = SourceType.PRICE_ALERT;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum SourceType {
        PRICE_ALERT,
        BUDGET_RULE
    }
}
