import client from './client';
import type { Asset, AssetType } from '@/types/portfolio.types';

/** API methods for browsing the asset master list. */
export const assetApi = {
  list: async (type?: AssetType): Promise<Asset[]> => {
    const { data } = await client.get<Asset[]>('/assets', {
      params: type ? { type } : undefined,
    });
    return data;
  },
};
