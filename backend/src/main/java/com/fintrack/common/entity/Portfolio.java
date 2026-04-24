package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/** Container for a group of investments. A user can have multiple portfolios. */
@Entity
@Table(name = "portfolios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "portfolio_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PortfolioType portfolioType = PortfolioType.INDIVIDUAL;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum PortfolioType {
        INDIVIDUAL,
        BES,
        RETIREMENT,
        EMERGENCY,
        STOCKS,
        CRYPTO,
        REAL_ESTATE,
        OTHER
    }
}
