package com.fintrack.portfolio.snapshot;

import com.fintrack.common.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link PortfolioSnapshot}. Snapshots are unique per
 * (portfolio_id, snapshot_date) so the scheduler can upsert safely.
 */
@Repository
public interface SnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

    /** Chronological history for a single portfolio. */
    List<PortfolioSnapshot> findByPortfolioIdOrderBySnapshotDateAsc(UUID portfolioId);

    /** Looks up an existing snapshot for the given day so we can update instead of insert. */
    Optional<PortfolioSnapshot> findByPortfolioIdAndSnapshotDate(UUID portfolioId, LocalDate snapshotDate);

    /**
     * Sum of the most recent total value across every portfolio. Used by the
     * business metrics exporter to publish a single aggregate gauge.
     */
    @Query("SELECT COALESCE(SUM(s.totalValueTry), 0) FROM PortfolioSnapshot s " +
           "WHERE s.snapshotDate = (SELECT MAX(s2.snapshotDate) FROM PortfolioSnapshot s2 WHERE s2.portfolioId = s.portfolioId)")
    BigDecimal sumLatestTotalValueTry();
}
