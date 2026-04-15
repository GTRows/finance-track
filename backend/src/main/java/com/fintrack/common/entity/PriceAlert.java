package com.fintrack.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A user-configured price alert on an asset. When the asset's current TRY
 * price crosses the threshold in the requested direction, the alert is
 * flipped to TRIGGERED and a notification row is persisted.
 */
@Entity
@Table(name = "price_alerts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(name = "threshold_try", nullable = false, precision = 20, scale = 6)
    private BigDecimal thresholdTry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    public enum Direction {
        ABOVE, BELOW
    }

    public enum Status {
        ACTIVE, TRIGGERED, DISABLED
    }
}
