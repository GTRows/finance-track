package com.fintrack.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_category_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pattern;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "txn_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private BudgetTransaction.TxnType txnType;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "match_count", nullable = false)
    @Builder.Default
    private Integer matchCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
