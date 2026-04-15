import client from './client';
import type { Asset, AssetType } from '@/types/portfolio.types';

export interface PricePoint {
  recordedAt: string;
  price: number;
  priceUsd: number | null;
}

export type TefasFundType = 'YAT' | 'EMK';

export interface TefasFundSearchRow {
  code: string;
  name: string;
  type: TefasFundType;
  imported: boolean;
}

/** API methods for browsing the asset master list. */
export const assetApi = {
  list: async (type?: AssetType): Promise<Asset[]> => {
    const { data } = await client.get<Asset[]>('/assets', {
      params: type ? { type } : undefined,
    });
    return data;
  },
  get: async (assetId: string): Promise<Asset> => {
    const { data } = await client.get<Asset>(`/assets/${assetId}`);
    return data;
  },
  history: async (assetId: string, days = 30): Promise<PricePoint[]> => {
    const { data } = await client.get<PricePoint[]>(`/assets/${assetId}/history`, {
      params: { days },
    });
    return data;
  },
  searchTefas: async (query: string): Promise<TefasFundSearchRow[]> => {
    const { data } = await client.get<TefasFundSearchRow[]>('/assets/tefas/search', {
      params: { q: query },
    });
    return data;
  },
  importTefas: async (code: string, type: TefasFundType): Promise<Asset> => {
    const { data } = await client.post<Asset>('/assets/tefas/import', null, {
      params: { code, type },
    });
    return data;
  },
};
