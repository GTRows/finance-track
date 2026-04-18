import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { holdingApi } from '@/api/holding.api';
import type { AddHoldingRequest } from '@/types/portfolio.types';

const holdingsKey = (portfolioId: string) => ['portfolios', portfolioId, 'holdings'] as const;

/** Fetches the holdings for a portfolio. Auto-refetches every 15 seconds so
 *  the UI picks up fresh prices as soon as the backend scheduler writes them. */
export function useHoldings(portfolioId: string | undefined) {
  return useQuery({
    queryKey: holdingsKey(portfolioId ?? ''),
    queryFn: () => holdingApi.list(portfolioId as string),
    enabled: !!portfolioId,
    refetchInterval: 15_000,
    refetchIntervalInBackground: false,
  });
}

/** Adds a new holding and refreshes the list. */
export function useAddHolding(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: AddHoldingRequest) => holdingApi.add(portfolioId, request),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: holdingsKey(portfolioId) });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/** Removes a holding and refreshes the list. */
export function useDeleteHolding(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (holdingId: string) => holdingApi.delete(portfolioId, holdingId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: holdingsKey(portfolioId) });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/** Toggles the pinned flag on a holding. Optimistically updates the cache so
 *  the star flips instantly; React Query's refetch reconciles on error. */
export function useToggleHoldingPin(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (holdingId: string) => holdingApi.togglePin(portfolioId, holdingId),
    onMutate: async (holdingId) => {
      await qc.cancelQueries({ queryKey: holdingsKey(portfolioId) });
      const prev = qc.getQueryData<ReturnType<typeof holdingApi.list> extends Promise<infer T> ? T : never>(
        holdingsKey(portfolioId)
      );
      if (prev) {
        qc.setQueryData(
          holdingsKey(portfolioId),
          prev.map((h) => (h.id === holdingId ? { ...h, pinned: !h.pinned } : h))
        );
      }
      return { prev };
    },
    onError: (_err, _id, ctx) => {
      if (ctx?.prev) qc.setQueryData(holdingsKey(portfolioId), ctx.prev);
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: holdingsKey(portfolioId) });
    },
  });
}
