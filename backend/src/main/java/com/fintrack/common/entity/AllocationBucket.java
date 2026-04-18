package com.fintrack.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A named cash-flow bucket (e.g. "Savings", "Investments") owned by a user.
 * Percentages are applied against discretionary income — the leftover after
 * obligatory outflows. Multiple buckets per user, ordered by {@code ordinal}.
 */
@Entity
@Table(name = "allocation_buckets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false)
    @Builder.Default
    private Integer ordinal = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
