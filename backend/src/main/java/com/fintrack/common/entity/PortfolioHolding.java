package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

/** Current position in each asset within a portfolio. Updated on every investment transaction. */
@Entity
@Table(
        name = "portfolio_holdings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "asset_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false, precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "avg_cost_try", precision = 20, scale = 4)
    private BigDecimal avgCostTry;

    @Column(name = "target_weight", precision = 5, scale = 4)
    private BigDecimal targetWeight;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
