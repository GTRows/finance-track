package com.fintrack.price;

import com.fintrack.common.entity.PriceHistory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {

    @Query(
            "SELECT p FROM PriceHistory p WHERE p.assetId = :assetId AND p.recordedAt >= :since"
                    + " ORDER BY p.recordedAt ASC")
    List<PriceHistory> findSeries(@Param("assetId") UUID assetId, @Param("since") Instant since);

    /**
     * Returns chronologically-ordered price history rows for an asset within a bounded time range
     * (inclusive of {@code from}, exclusive of {@code to} per JPA range semantics for the BETWEEN
     * keyword which is inclusive on both ends — callers should pre-clamp to avoid off-by-one).
     */
    List<PriceHistory> findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            UUID assetId, Instant from, Instant to);
}
