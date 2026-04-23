import { beforeEach, describe, expect, it } from 'vitest';
import { useLivePricesStore } from './livePrices.store';

describe('useLivePricesStore', () => {
  beforeEach(() => {
    useLivePricesStore.setState({ prices: {}, publishedAt: null });
  });

  it('starts empty', () => {
    const s = useLivePricesStore.getState();
    expect(s.prices).toEqual({});
    expect(s.publishedAt).toBeNull();
  });

  it('applyBatch seeds prices with null previousPrice on first publish', () => {
    useLivePricesStore.getState().applyBatch({
      publishedAt: '2026-04-01T00:00:00Z',
      prices: [
        { symbol: 'BTC', assetType: 'CRYPTO', price: 100, priceUsd: 3, updatedAt: '2026-04-01T00:00:00Z' },
        { symbol: 'ETH', assetType: 'CRYPTO', price: 50, priceUsd: null, updatedAt: '2026-04-01T00:00:00Z' },
      ],
    });

    const s = useLivePricesStore.getState();
    expect(s.publishedAt).toBe('2026-04-01T00:00:00Z');
    expect(s.prices.BTC.price).toBe(100);
    expect(s.prices.BTC.previousPrice).toBeNull();
    expect(s.prices.ETH.priceUsd).toBeNull();
  });

  it('applyBatch carries the prior price into previousPrice on update', () => {
    const store = useLivePricesStore;
    store.getState().applyBatch({
      publishedAt: '2026-04-01T00:00:00Z',
      prices: [{ symbol: 'BTC', assetType: 'CRYPTO', price: 100, priceUsd: 3, updatedAt: '2026-04-01T00:00:00Z' }],
    });

    store.getState().applyBatch({
      publishedAt: '2026-04-01T00:00:10Z',
      prices: [{ symbol: 'BTC', assetType: 'CRYPTO', price: 110, priceUsd: 3.2, updatedAt: '2026-04-01T00:00:10Z' }],
    });

    const s = store.getState();
    expect(s.prices.BTC.price).toBe(110);
    expect(s.prices.BTC.previousPrice).toBe(100);
    expect(s.publishedAt).toBe('2026-04-01T00:00:10Z');
  });

  it('applyBatch leaves unmentioned symbols untouched', () => {
    useLivePricesStore.getState().applyBatch({
      publishedAt: '2026-04-01T00:00:00Z',
      prices: [
        { symbol: 'BTC', assetType: 'CRYPTO', price: 100, priceUsd: null, updatedAt: '2026-04-01T00:00:00Z' },
        { symbol: 'ETH', assetType: 'CRYPTO', price: 50, priceUsd: null, updatedAt: '2026-04-01T00:00:00Z' },
      ],
    });

    useLivePricesStore.getState().applyBatch({
      publishedAt: '2026-04-01T00:01:00Z',
      prices: [{ symbol: 'BTC', assetType: 'CRYPTO', price: 150, priceUsd: null, updatedAt: '2026-04-01T00:01:00Z' }],
    });

    const s = useLivePricesStore.getState();
    expect(s.prices.BTC.price).toBe(150);
    expect(s.prices.BTC.previousPrice).toBe(100);
    expect(s.prices.ETH.price).toBe(50);
    expect(s.prices.ETH.previousPrice).toBeNull();
  });

  it('applyBatch with empty prices list only updates publishedAt', () => {
    useLivePricesStore.setState({ prices: {}, publishedAt: 'old' });

    useLivePricesStore.getState().applyBatch({
      publishedAt: '2026-04-01T00:00:00Z',
      prices: [],
    });

    expect(useLivePricesStore.getState().prices).toEqual({});
    expect(useLivePricesStore.getState().publishedAt).toBe('2026-04-01T00:00:00Z');
  });
});
