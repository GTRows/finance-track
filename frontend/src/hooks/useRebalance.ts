import { useMutation, useQueryClient } from '@tanstack/react-query';
import { rebalanceApi } from '@/api/rebalance.api';
import type {
  RebalanceCommitRequest,
  RebalancePreviewRequest,
  UpdateRebalanceThresholdRequest,
} from '@/types/rebalance.types';

export function useRebalancePreview(portfolioId: string) {
  return useMutation({
    mutationFn: (request: RebalancePreviewRequest) => rebalanceApi.preview(portfolioId, request),
  });
}

export function useRebalanceCommit(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: RebalanceCommitRequest) => rebalanceApi.commit(portfolioId, request),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['portfolios', portfolioId, 'holdings'] });
      void qc.invalidateQueries({ queryKey: ['transactions', portfolioId] });
      void qc.invalidateQueries({ queryKey: ['allocation', portfolioId] });
      void qc.invalidateQueries({ queryKey: ['accounts'] });
      void qc.invalidateQueries({ queryKey: ['accounts', 'totals'] });
    },
  });
}

export function useUpdateRebalanceThreshold() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateRebalanceThresholdRequest) => rebalanceApi.updateThreshold(request),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['settings'] });
    },
  });
}
