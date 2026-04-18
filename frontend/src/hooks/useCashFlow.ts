import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cashFlowApi } from '@/api/cashflow.api';
import type {
  CashFlowBucketInput,
  CashFlowPreviewRequest,
} from '@/types/cashflow.types';

const bucketsKey = ['cashflow', 'buckets'] as const;

export function useCashFlowBuckets() {
  return useQuery({
    queryKey: bucketsKey,
    queryFn: cashFlowApi.listBuckets,
  });
}

export function useReplaceCashFlowBuckets() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (buckets: CashFlowBucketInput[]) => cashFlowApi.replaceBuckets(buckets),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: bucketsKey });
    },
  });
}

export function useCashFlowPreview() {
  return useMutation({
    mutationFn: (req: CashFlowPreviewRequest) => cashFlowApi.preview(req),
  });
}
