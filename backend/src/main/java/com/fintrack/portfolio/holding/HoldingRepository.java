package com.fintrack.portfolio.holding;

import com.fintrack.common.entity.PortfolioHolding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** JPA repository for {@link PortfolioHolding} positions. */
@Repository
public interface HoldingRepository extends JpaRepository<PortfolioHolding, UUID> {

    /** Returns all holdings for a portfolio joined with asset data, sorted by symbol. */
    @Query(
            """
            SELECT h FROM PortfolioHolding h
            WHERE h.portfolioId = :portfolioId
            """)
    List<PortfolioHolding> findByPortfolioId(@Param("portfolioId") UUID portfolioId);

    /** Looks up a holding by its portfolio and asset (used to prevent duplicates). */
    Optional<PortfolioHolding> findByPortfolioIdAndAssetId(UUID portfolioId, UUID assetId);

    /** Returns a holding by id constrained to a specific portfolio. */
    Optional<PortfolioHolding> findByIdAndPortfolioId(UUID id, UUID portfolioId);
}
