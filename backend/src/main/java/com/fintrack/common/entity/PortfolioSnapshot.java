package com.fintrack.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Daily point-in-time portfolio valuation used for historical charts.
 */
@Entity
@Table(name = "portfolio_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "snapshot_date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_value_try", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalValueTry;

    @Column(name = "total_cost_try", precision = 20, scale = 4)
    private BigDecimal totalCostTry;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "holdings_json", columnDefinition = "jsonb")
    private Map<String, Object> holdingsJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
