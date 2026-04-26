import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { portfolioApi } from '@/api/portfolio.api';
import type { CreatePortfolioRequest, UpdatePortfolioRequest } from '@/types/portfolio.types';

const PORTFOLIOS_KEY = ['portfolios'] as const;

/** Fetches the list of portfolios for the authenticated user. */
export function usePortfolios() {
  return useQuery({
    queryKey: PORTFOLIOS_KEY,
    queryFn: portfolioApi.list,
  });
}

/** Fetches a single portfolio by id. */
export function usePortfolio(id: string | undefined) {
  return useQuery({
    queryKey: ['portfolios', id],
    queryFn: () => portfolioApi.get(id as string),
    enabled: !!id,
  });
}

/** Creates a new portfolio and refreshes the list. */
export function useCreatePortfolio() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: CreatePortfolioRequest) => portfolioApi.create(request),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: PORTFOLIOS_KEY });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/** Updates a portfolio and refreshes the list. */
export function useUpdatePortfolio() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdatePortfolioRequest }) =>
      portfolioApi.update(id, request),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: PORTFOLIOS_KEY });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/** Deletes a portfolio and refreshes the list. */
export function useDeletePortfolio() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => portfolioApi.delete(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: PORTFOLIOS_KEY });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
