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
 * Every income or expense entry recorded by the user.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "txn_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private TxnType txnType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(length = 255)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private boolean recurring = false;

    @Column(name = "recurrence_rule", length = 100)
    private String recurrenceRule;

    @Column(name = "receipt_path", length = 512)
    private String receiptPath;

    @Column(name = "original_amount", precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "original_currency", length = 10)
    private String originalCurrency;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum TxnType {
        INCOME, EXPENSE
    }
}
