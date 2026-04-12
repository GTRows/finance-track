import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';

const PRICES_TOPIC = '/topic/prices';

/**
 * Connects to the backend STOMP endpoint and listens for price broadcasts.
 * On each message, invalidates all holdings queries so React Query refetches
 * with the updated prices already in the DB.
 *
 * Connects once, reconnects automatically on failure, and disconnects when
 * the calling component unmounts.
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
      stompClient.subscribe(PRICES_TOPIC, () => {
        queryClient.invalidateQueries({ queryKey: ['portfolios'] });
      });
    };

    stompClient.activate();
    clientRef.current = stompClient;

    return () => {
      if (clientRef.current?.active) {
        clientRef.current.deactivate();
      }
    };
  }, [queryClient]);
}
