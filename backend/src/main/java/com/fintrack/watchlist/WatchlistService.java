package com.fintrack.watchlist;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.WatchlistEntry;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.watchlist.dto.AddWatchlistRequest;
import com.fintrack.watchlist.dto.WatchlistEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository repository;
    private final AssetRepository assetRepository;

    @Transactional(readOnly = true)
    public List<WatchlistEntryResponse> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(WatchlistEntryResponse::from)
                .toList();
    }

    @Transactional
    public WatchlistEntryResponse add(UUID userId, AddWatchlistRequest request) {
        if (!assetRepository.existsById(request.assetId())) {
            throw new ResourceNotFoundException("Asset not found");
        }
        WatchlistEntry entry = repository.findByUserIdAndAssetId(userId, request.assetId())
                .orElseGet(() -> WatchlistEntry.builder()
                        .userId(userId)
                        .assetId(request.assetId())
                        .build());
        entry.setNote(request.note());
        return WatchlistEntryResponse.from(repository.save(entry));
    }

    @Transactional
    public void remove(UUID userId, UUID assetId) {
        repository.deleteByUserIdAndAssetId(userId, assetId);
    }
}
