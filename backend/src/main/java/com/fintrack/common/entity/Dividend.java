package com.fintrack.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dividends")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "amount_per_share", precision = 20, scale = 8)
    private BigDecimal amountPerShare;

    @Column(precision = 20, scale = 8)
    private BigDecimal shares;

    @Column(name = "gross_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "withholding_tax", nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal withholdingTax = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "net_amount_try", nullable = false, precision = 20, scale = 4)
    private BigDecimal netAmountTry;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "ex_dividend_date")
    private LocalDate exDividendDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
