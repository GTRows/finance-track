import { useCallback, useEffect, useState } from 'react';
import { pushApi } from '@/api/push.api';

type SupportState = 'checking' | 'unsupported' | 'supported';
type PermissionState = 'default' | 'granted' | 'denied';

function urlBase64ToArrayBuffer(base64: string): ArrayBuffer {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const normalized = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(normalized);
  const buffer = new ArrayBuffer(raw.length);
  const view = new Uint8Array(buffer);
  for (let i = 0; i < raw.length; i++) view[i] = raw.charCodeAt(i);
  return buffer;
}

function arrayBufferToBase64Url(buffer: ArrayBuffer | null): string {
  if (!buffer) return '';
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function usePush() {
  const [support, setSupport] = useState<SupportState>('checking');
  const [permission, setPermission] = useState<PermissionState>('default');
  const [subscribed, setSubscribed] = useState<boolean>(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (typeof window === 'undefined') return;
    if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
      setSupport('unsupported');
      return;
    }
    setSupport('supported');
    setPermission(Notification.permission as PermissionState);
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      if (!reg) {
        setSubscribed(false);
        return;
      }
      const sub = await reg.pushManager.getSubscription();
      setSubscribed(!!sub);
    } catch {
      setSubscribed(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const subscribe = useCallback(async () => {
    if (support !== 'supported') return;
    setBusy(true);
    setError(null);
    try {
      const perm = await Notification.requestPermission();
      setPermission(perm as PermissionState);
      if (perm !== 'granted') {
        setError('permission-denied');
        return;
      }
      let reg = await navigator.serviceWorker.getRegistration();
      if (!reg) {
        reg = await navigator.serviceWorker.register('/sw.js');
      }
      await navigator.serviceWorker.ready;

      const { publicKey } = await pushApi.getVapidPublicKey();
      const applicationServerKey = urlBase64ToArrayBuffer(publicKey);

      let sub = await reg.pushManager.getSubscription();
      if (!sub) {
        sub = await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey,
        });
      }

      const p256dh = arrayBufferToBase64Url(sub.getKey('p256dh'));
      const auth = arrayBufferToBase64Url(sub.getKey('auth'));
      await pushApi.subscribe({ endpoint: sub.endpoint, p256dh, auth });
      setSubscribed(true);
    } catch (e) {
      setError((e as Error).message ?? 'unknown');
    } finally {
      setBusy(false);
    }
  }, [support]);

  const unsubscribe = useCallback(async () => {
    if (support !== 'supported') return;
    setBusy(true);
    setError(null);
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      const sub = await reg?.pushManager.getSubscription();
      if (sub) {
        await pushApi.unsubscribe(sub.endpoint);
        await sub.unsubscribe();
      }
      setSubscribed(false);
    } catch (e) {
      setError((e as Error).message ?? 'unknown');
    } finally {
      setBusy(false);
    }
  }, [support]);

  const sendTest = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      await pushApi.test();
    } catch (e) {
      setError((e as Error).message ?? 'unknown');
    } finally {
      setBusy(false);
    }
  }, []);

  return {
    support,
    permission,
    subscribed,
    busy,
    error,
    subscribe,
    unsubscribe,
    sendTest,
    refresh,
  };
}
