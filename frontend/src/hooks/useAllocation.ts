import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { allocationApi } from '@/api/allocation.api';
import type { SetAllocationRequest } from '@/types/allocation.types';

const key = (portfolioId: string) => ['allocation', portfolioId] as const;

export function useAllocation(portfolioId: string | undefined) {
  return useQuery({
    queryKey: key(portfolioId ?? ''),
    queryFn: () => allocationApi.get(portfolioId!),
    enabled: !!portfolioId,
  });
}

export function useSetAllocation(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: SetAllocationRequest) => allocationApi.set(portfolioId, req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: key(portfolioId) });
    },
  });
}
