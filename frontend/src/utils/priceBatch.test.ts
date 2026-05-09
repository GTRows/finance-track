import { describe, expect, it } from 'vitest';
import { mergePriceBatch, type LivePriceRow, type LivePricesBatch } from './priceBatch';

const row = (overrides: Partial<LivePriceRow> = {}): LivePriceRow => ({
  symbol: 'BTC',
  assetType: 'CRYPTO',
  price: 100,
  priceUsd: 3,
  updatedAt: '2026-04-01T00:00:00Z',
  ...overrides,
});

const batch = (overrides: Partial<LivePricesBatch> & Pick<LivePricesBatch, 'prices'>): LivePricesBatch => ({
  publishedAt: '2026-04-01T00:00:00Z',
  count: overrides.prices.length,
  totalAssets: overrides.prices.length,
  deltaOnly: true,
  ...overrides,
});

describe('mergePriceBatch', () => {
  it('keeps the prior map intact when the delta is empty', () => {
    const prev: Record<string, LivePriceRow> = { BTC: row(), ETH: row({ symbol: 'ETH', price: 50 }) };
    const merged = mergePriceBatch(prev, batch({ prices: [] }));
    expect(merged).toEqual(prev);
    expect(merged).not.toBe(prev);
  });

  it('adds a new asset on a delta tick', () => {
    const prev: Record<string, LivePriceRow> = { BTC: row() };
    const merged = mergePriceBatch(
      prev,
      batch({ prices: [row({ symbol: 'ETH', price: 50 })] }),
    );
    expect(Object.keys(merged).sort()).toEqual(['BTC', 'ETH']);
    expect(merged.ETH.price).toBe(50);
    expect(merged.BTC.price).toBe(100);
  });

  it('overwrites the value for an existing asset on a delta tick', () => {
    const prev: Record<string, LivePriceRow> = { BTC: row({ price: 100 }) };
    const merged = mergePriceBatch(
      prev,
      batch({ prices: [row({ price: 110 })] }),
    );
    expect(merged.BTC.price).toBe(110);
  });

  it('replaces the entire map when deltaOnly is false', () => {
    const prev: Record<string, LivePriceRow> = {
      BTC: row(),
      OLD: row({ symbol: 'OLD', price: 7 }),
    };
    const merged = mergePriceBatch(
      prev,
      batch({ deltaOnly: false, prices: [row({ symbol: 'ETH', price: 50 })] }),
    );
    expect(Object.keys(merged)).toEqual(['ETH']);
    expect(merged.ETH.price).toBe(50);
    expect(merged.BTC).toBeUndefined();
    expect(merged.OLD).toBeUndefined();
  });

  it('accumulates two consecutive delta batches correctly', () => {
    let map: Record<string, LivePriceRow> = {};
    map = mergePriceBatch(map, batch({ prices: [row({ symbol: 'BTC', price: 100 })] }));
    map = mergePriceBatch(
      map,
      batch({
        publishedAt: '2026-04-01T00:00:30Z',
        prices: [row({ symbol: 'ETH', price: 50 })],
      }),
    );
    expect(map.BTC.price).toBe(100);
    expect(map.ETH.price).toBe(50);
  });
});
