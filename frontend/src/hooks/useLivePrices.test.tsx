import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';

import { useLivePricesStore } from '@/store/livePrices.store';
import { createWrapper } from '@/test-utils/queryWrapper';

type StompFrame = { body: string };

// Capture topic subscribers per renderHook so the test can drive the message stream.
const callbacks = new Map<string, (frame: StompFrame) => void>();

vi.mock('@stomp/stompjs', () => {
  return {
    Client: class MockClient {
      onConnect: () => void = () => {};
      constructor(_opts: unknown) {
        void _opts;
      }
      activate() {
        // Trigger onConnect immediately so the subscribe callback is registered.
        this.onConnect();
      }
      deactivate() {
        return Promise.resolve();
      }
      subscribe(topic: string, cb: (frame: StompFrame) => void) {
        callbacks.set(topic, cb);
        return { id: '1', unsubscribe: () => {} };
      }
      get active() {
        return true;
      }
    },
  };
});

function pushFrame(topic: string, body: unknown) {
  const cb = callbacks.get(topic);
  if (!cb) throw new Error(`No subscriber for ${topic}`);
  cb({ body: JSON.stringify(body) });
}

describe('useLivePrices', () => {
  beforeEach(() => {
    callbacks.clear();
    useLivePricesStore.setState({ prices: {}, publishedAt: null });
  });

  it('routes deltaOnly=true frames through mergeBatch (preserves prior symbols)', async () => {
    // Seed prior state via mergeBatch (cold-boot full).
    useLivePricesStore.getState().mergeBatch({
      publishedAt: '2026-04-01T00:00:00Z',
      count: 2,
      totalAssets: 2,
      deltaOnly: false,
      prices: [
        { symbol: 'BTC', assetType: 'CRYPTO', price: 100, priceUsd: null, updatedAt: '2026-04-01T00:00:00Z' },
        { symbol: 'ETH', assetType: 'CRYPTO', price: 50, priceUsd: null, updatedAt: '2026-04-01T00:00:00Z' },
      ],
    });

    const { useLivePrices } = await import('./useLivePrices');
    const { Wrapper } = createWrapper();
    renderHook(() => useLivePrices(), { wrapper: Wrapper });

    pushFrame('/topic/prices', {
      publishedAt: '2026-04-01T00:00:30Z',
      count: 1,
      totalAssets: 2,
      deltaOnly: true,
      prices: [
        { symbol: 'BTC', assetType: 'CRYPTO', price: 110, priceUsd: null, updatedAt: '2026-04-01T00:00:30Z' },
      ],
    });

    const s = useLivePricesStore.getState();
    expect(s.prices.BTC.price).toBe(110);
    expect(s.prices.BTC.previousPrice).toBe(100);
    // ETH must remain because deltaOnly=true means merge, not replace.
    expect(s.prices.ETH.price).toBe(50);
    expect(s.publishedAt).toBe('2026-04-01T00:00:30Z');
  });
});
