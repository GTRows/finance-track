package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "portfolio_allocation_targets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "asset_type"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioAllocationTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "asset_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Asset.AssetType assetType;

    @Column(name = "target_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetPercent;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
