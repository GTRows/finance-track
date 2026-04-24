package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/** Recurring bills to track and pay monthly. */
@Entity
@Table(name = "bills")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "TRY";

    @Column(length = 50)
    private String category;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "auto_pay", nullable = false)
    @Builder.Default
    private boolean autoPay = false;

    @Column(name = "remind_days_before", nullable = false)
    @Builder.Default
    private int remindDaysBefore = 3;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_reminded_on")
    private LocalDate lastRemindedOn;

    @Column(name = "last_used_on")
    private LocalDate lastUsedOn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
