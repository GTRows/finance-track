package com.fintrack.watchlist;

import com.fintrack.common.entity.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistEntry, UUID> {

    List<WatchlistEntry> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<WatchlistEntry> findByUserIdAndAssetId(UUID userId, UUID assetId);

    boolean existsByUserIdAndAssetId(UUID userId, UUID assetId);

    void deleteByUserIdAndAssetId(UUID userId, UUID assetId);
}
