import { useMutation, useQueryClient } from '@tanstack/react-query';
import { priceApi } from '@/api/price.api';

/**
 * Triggers a manual price refresh on the backend and invalidates any cached
 * holdings/assets so the UI pulls the fresh numbers immediately.
 */
export function useRefreshPrices() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: priceApi.refresh,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['assets'] });
      qc.invalidateQueries({ queryKey: ['portfolios'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
