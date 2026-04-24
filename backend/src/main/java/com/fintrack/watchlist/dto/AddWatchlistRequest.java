package com.fintrack.watchlist.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddWatchlistRequest(@NotNull UUID assetId, String note) {}
