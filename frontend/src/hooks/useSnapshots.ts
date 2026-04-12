import { useQuery } from '@tanstack/react-query';
import { snapshotApi } from '@/api/snapshot.api';

const snapshotsKey = (portfolioId: string) => ['portfolios', portfolioId, 'history'] as const;

/** Fetches the historical value series for a portfolio. */
export function useSnapshots(portfolioId: string | undefined) {
  return useQuery({
    queryKey: snapshotsKey(portfolioId ?? ''),
    queryFn: () => snapshotApi.list(portfolioId as string),
    enabled: !!portfolioId,
    staleTime: 60_000,
  });
}
