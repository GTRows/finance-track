package com.fintrack.price;

import com.fintrack.common.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {

    @Query("SELECT p FROM PriceHistory p WHERE p.assetId = :assetId AND p.recordedAt >= :since ORDER BY p.recordedAt ASC")
    List<PriceHistory> findSeries(@Param("assetId") UUID assetId, @Param("since") Instant since);
}
