package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "recurring_templates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "txn_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private BudgetTransaction.TxnType txnType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(length = 255)
    private String description;

    @Column(name = "day_of_month", nullable = false)
    private Integer dayOfMonth;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_materialized_on")
    private LocalDate lastMaterializedOn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
