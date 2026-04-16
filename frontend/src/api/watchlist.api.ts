import client from './client';
import type { AddWatchlistRequest, WatchlistEntry } from '@/types/watchlist.types';

export const watchlistApi = {
  list: async (): Promise<WatchlistEntry[]> => {
    const { data } = await client.get<WatchlistEntry[]>('/watchlist');
    return data;
  },
  add: async (req: AddWatchlistRequest): Promise<WatchlistEntry> => {
    const { data } = await client.post<WatchlistEntry>('/watchlist', req);
    return data;
  },
  remove: async (assetId: string): Promise<void> => {
    await client.delete(`/watchlist/${assetId}`);
  },
};
