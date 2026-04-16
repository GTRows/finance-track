package com.fintrack.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * User-anchored note on the combined net worth timeline
 * (big purchases, bonus payouts, milestones, general notes).
 */
@Entity
@Table(name = "net_worth_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetWorthEvent {

    public enum EventType {
        PURCHASE,
        INCOME,
        MILESTONE,
        NOTE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "impact_try", precision = 20, scale = 2)
    private BigDecimal impactTry;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
