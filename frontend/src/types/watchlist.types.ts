export interface WatchlistEntry {
  assetId: string;
  note: string | null;
  createdAt: string;
}

export interface AddWatchlistRequest {
  assetId: string;
  note?: string | null;
}
