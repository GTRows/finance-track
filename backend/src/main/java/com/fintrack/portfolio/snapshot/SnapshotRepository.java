package com.fintrack.portfolio.snapshot;

import com.fintrack.common.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
