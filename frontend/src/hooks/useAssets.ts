import { useQuery } from '@tanstack/react-query';
import { assetApi } from '@/api/asset.api';
import type { AssetType } from '@/types/portfolio.types';

/** Fetches the asset master list, optionally filtered by type. */
export function useAssets(type?: AssetType) {
  return useQuery({
    queryKey: ['assets', type ?? 'all'],
    queryFn: () => assetApi.list(type),
    staleTime: 5 * 60 * 1000,
  });
}
