package com.fintrack.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full audit log of every portfolio action (buy, sell, deposit, withdraw, etc.).
 */
@Entity
@Table(name = "investment_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "txn_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private TxnType txnType;

    @Column(precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "price_try", precision = 20, scale = 4)
    private BigDecimal priceTry;

    @Column(name = "amount_try", nullable = false, precision = 20, scale = 4)
    private BigDecimal amountTry;

    @Column(name = "fee_try", precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal feeTry = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum TxnType {
        BUY, SELL, DEPOSIT, WITHDRAW, REBALANCE, BES_CONTRIBUTION
    }
}
