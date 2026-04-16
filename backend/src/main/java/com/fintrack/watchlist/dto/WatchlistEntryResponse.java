package com.fintrack.watchlist.dto;

import com.fintrack.common.entity.WatchlistEntry;

import java.time.Instant;
import java.util.UUID;

public record WatchlistEntryResponse(
        UUID assetId,
        String note,
        Instant createdAt
) {
    public static WatchlistEntryResponse from(WatchlistEntry entry) {
        return new WatchlistEntryResponse(entry.getAssetId(), entry.getNote(), entry.getCreatedAt());
    }
}
