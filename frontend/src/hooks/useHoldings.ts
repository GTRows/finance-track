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
    },
  });
}
