import { create } from 'zustand';

export interface LivePrice {
  symbol: string;
  assetType: string;
  price: number;
  priceUsd: number | null;
  updatedAt: string;
  previousPrice: number | null;
}

interface LivePricesState {
  prices: Record<string, LivePrice>;
  publishedAt: string | null;
  applyBatch: (batch: {
    publishedAt: string;
    prices: Array<Omit<LivePrice, 'previousPrice'>>;
  }) => void;
}

export const useLivePricesStore = create<LivePricesState>((set) => ({
  prices: {},
  publishedAt: null,
  applyBatch: (batch) =>
    set((state) => {
      const next: Record<string, LivePrice> = { ...state.prices };
      for (const p of batch.prices) {
        const prev = state.prices[p.symbol];
        next[p.symbol] = {
          ...p,
          previousPrice: prev ? prev.price : null,
        };
      }
      return { prices: next, publishedAt: batch.publishedAt };
    }),
}));
