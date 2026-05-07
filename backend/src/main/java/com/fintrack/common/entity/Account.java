package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Owner-scoped account: bank checking/savings, brokerage cash, crypto wallet, physical cash. Each
 * account carries one primary currency and one running balance. Wired to investment / budget
 * transactions in 27-03; in 27-02 it is a standalone declaration the owner edits by hand.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "account_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 100)
    private String institution;

    @Column(name = "account_number_suffix", length = 16)
    private String accountNumberSuffix;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "current_balance", nullable = false, precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private boolean archived = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum AccountType {
        BANK_CHECKING,
        BANK_SAVINGS,
        BROKERAGE_CASH,
        CRYPTO_WALLET,
        CASH,
        OTHER
    }
}
