import { create } from 'zustand';
import type { LivePricesBatch } from '@/utils/priceBatch';

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
  /**
   * Legacy merge-into-existing-state action. Kept alongside {@link mergeBatch} so other callers
   * that pass a 3-field batch envelope keep working.
   */
  applyBatch: (batch: {
    publishedAt: string;
    prices: Array<Omit<LivePrice, 'previousPrice'>>;
  }) => void;
  /**
   * WebSocket-aware action that consumes the {@link LivePricesBatch} envelope. When
   * `batch.deltaOnly === false` (cold-boot full broadcast) the prior map is replaced; when
   * `batch.deltaOnly === true` (steady-state delta) incoming rows are merged into the existing
   * map. {@code previousPrice} tracks the last value seen for each symbol either way.
   */
  mergeBatch: (batch: LivePricesBatch) => void;
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
  mergeBatch: (batch) =>
    set((state) => {
      const base: Record<string, LivePrice> = batch.deltaOnly ? { ...state.prices } : {};
      for (const p of batch.prices) {
        const prev = state.prices[p.symbol];
        base[p.symbol] = {
          symbol: p.symbol,
          assetType: p.assetType,
          price: p.price,
          priceUsd: p.priceUsd,
          updatedAt: p.updatedAt,
          previousPrice: prev ? prev.price : null,
        };
      }
      return { prices: base, publishedAt: batch.publishedAt };
    }),
}));
