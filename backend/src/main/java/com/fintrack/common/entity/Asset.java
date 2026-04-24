package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Master list of trackable financial instruments (crypto, funds, currencies). */
@Entity
@Table(
        name = "assets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "asset_type"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "asset_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "TRY";

    @Column(precision = 20, scale = 6)
    private BigDecimal price;

    @Column(name = "price_usd", precision = 20, scale = 6)
    private BigDecimal priceUsd;

    @Column(name = "price_updated_at")
    private Instant priceUpdatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum AssetType {
        CRYPTO,
        STOCK,
        GOLD,
        FUND,
        CURRENCY,
        OTHER
    }
}
