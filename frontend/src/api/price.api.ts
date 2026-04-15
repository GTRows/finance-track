import client from './client';

export interface PriceSyncResult {
  cryptoUpdated: number;
  currencyUpdated: number;
  runAt: string;
}

/** API methods for the price sync subsystem. */
export const priceApi = {
  refresh: async (): Promise<PriceSyncResult> => {
    const { data } = await client.post<PriceSyncResult>('/prices/refresh');
    return data;
  },
  refreshAsset: async (assetId: string): Promise<{ updated: boolean }> => {
    const { data } = await client.post<{ updated: boolean }>(`/prices/refresh/${assetId}`);
    return data;
  },
};
