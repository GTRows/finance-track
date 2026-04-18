import client from './client';

export interface VapidPublicKeyResponse {
  publicKey: string;
}

export interface SubscribeRequest {
  endpoint: string;
  p256dh: string;
  auth: string;
}

export interface SubscribeResponse {
  id: string;
  subscribed: boolean;
}

export interface TestResponse {
  delivered: number;
}

export const pushApi = {
  getVapidPublicKey: () =>
    client.get<VapidPublicKeyResponse>('/push/vapid-public-key').then((r) => r.data),
  subscribe: (req: SubscribeRequest) =>
    client.post<SubscribeResponse>('/push/subscribe', req).then((r) => r.data),
  unsubscribe: (endpoint: string) =>
    client.delete<void>('/push/subscribe', { data: { endpoint } }).then((r) => r.data),
  test: () => client.post<TestResponse>('/push/test').then((r) => r.data),
};
