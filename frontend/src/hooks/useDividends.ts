import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { dividendApi } from '@/api/dividend.api';
import type { RecordDividendRequest } from '@/types/dividend.types';

const dividendsKey = (portfolioId: string) => ['portfolios', portfolioId, 'dividends'] as const;

export function useDividends(portfolioId: string | undefined) {
  return useQuery({
    queryKey: dividendsKey(portfolioId ?? ''),
    queryFn: () => dividendApi.listForPortfolio(portfolioId as string),
    enabled: !!portfolioId,
  });
}

export function useRecordDividend(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: RecordDividendRequest) => dividendApi.record(portfolioId, request),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: dividendsKey(portfolioId) });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useDeleteDividend(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dividendId: string) => dividendApi.remove(portfolioId, dividendId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: dividendsKey(portfolioId) });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
