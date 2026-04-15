import { useQuery } from '@tanstack/react-query';
import { assetApi } from '@/api/asset.api';

/** Fetches a single asset by id. */
export function useAsset(assetId: string | undefined) {
  return useQuery({
    queryKey: ['asset', assetId],
    queryFn: () => assetApi.get(assetId!),
    enabled: !!assetId,
    staleTime: 60 * 1000,
  });
}

/** Fetches the recorded price history for an asset over the last N days. */
export function useAssetHistory(assetId: string | undefined, days = 30) {
  return useQuery({
    queryKey: ['asset-history', assetId, days],
    queryFn: () => assetApi.history(assetId!, days),
    enabled: !!assetId,
    staleTime: 30 * 1000,
  });
}
