import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { useLivePricesStore } from '@/store/livePrices.store';

const PRICES_TOPIC = '/topic/prices';

/**
 * Connects to the backend STOMP endpoint and listens for price broadcasts.
 * On each message, invalidates all holdings queries so React Query refetches
 * with the updated prices already in the DB.
 *
 * Connects once, reconnects automatically on failure, and disconnects when
 * the calling component unmounts.
 *
 * Frame envelope: `{ publishedAt, count, totalAssets, deltaOnly, prices: [...] }`. When
 * `deltaOnly === true`, only the changed prices arrive; the store's `mergeBatch` keeps stale
 * symbols around. When `deltaOnly === false`, the store replaces the entire price map (cold-boot
 * full broadcast). Older payloads without `deltaOnly` default to `false` so the merge falls back
 * to replacement.
 */
export function useLivePrices() {
  const queryClient = useQueryClient();
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = `${protocol}//${window.location.host}/ws`;

    const stompClient = new Client({
      brokerURL,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    stompClient.onConnect = () => {
      stompClient.subscribe(PRICES_TOPIC, (frame) => {
        try {
          const batch = JSON.parse(frame.body);
          if (batch && Array.isArray(batch.prices)) {
            const prices = batch.prices.map((p: {
              symbol: string;
              assetType: string;
              price: string | number;
              priceUsd: string | number | null;
              updatedAt: string;
            }) => ({
              symbol: p.symbol,
              assetType: p.assetType,
              price: typeof p.price === 'string' ? Number(p.price) : p.price,
              priceUsd:
                p.priceUsd == null
                  ? null
                  : typeof p.priceUsd === 'string'
                    ? Number(p.priceUsd)
                    : p.priceUsd,
              updatedAt: p.updatedAt,
            }));
            useLivePricesStore.getState().mergeBatch({
              publishedAt: batch.publishedAt,
              count: typeof batch.count === 'number' ? batch.count : prices.length,
              totalAssets:
                typeof batch.totalAssets === 'number' ? batch.totalAssets : prices.length,
              deltaOnly: typeof batch.deltaOnly === 'boolean' ? batch.deltaOnly : false,
              prices,
            });
          }
        } catch {
          // Ignore malformed frames; holdings will still refetch below.
        }
        void queryClient.invalidateQueries({ queryKey: ['portfolios'] });
        void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      });
    };

    stompClient.activate();
    clientRef.current = stompClient;

    return () => {
      if (clientRef.current?.active) {
        void clientRef.current.deactivate();
      }
    };
  }, [queryClient]);
}
