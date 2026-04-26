import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { watchlistApi } from '@/api/watchlist.api';

const watchlistKey = () => ['watchlist'] as const;

export function useWatchlist() {
  const query = useQuery({
    queryKey: watchlistKey(),
    queryFn: watchlistApi.list,
    staleTime: 60_000,
  });
  const ids = useMemo(
    () => new Set((query.data ?? []).map((e) => e.assetId)),
    [query.data],
  );
  return { ...query, ids };
}

export function useToggleWatchlist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ assetId, watched }: { assetId: string; watched: boolean }) => {
      if (watched) {
        await watchlistApi.remove(assetId);
      } else {
        await watchlistApi.add({ assetId });
      }
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: watchlistKey() });
    },
  });
}
