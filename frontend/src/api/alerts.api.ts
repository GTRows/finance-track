import client from './client';
import type {
  AlertNotification,
  CreateAlertRequest,
  PriceAlert,
  UnreadCount,
} from '@/types/alert.types';

export const alertsApi = {
  list: async (): Promise<PriceAlert[]> => {
    const { data } = await client.get<PriceAlert[]>('/alerts');
    return data;
  },
  create: async (req: CreateAlertRequest): Promise<PriceAlert> => {
    const { data } = await client.post<PriceAlert>('/alerts', req);
    return data;
  },
  delete: async (id: string): Promise<void> => {
    await client.delete(`/alerts/${id}`);
  },
  disable: async (id: string): Promise<PriceAlert> => {
    const { data } = await client.post<PriceAlert>(`/alerts/${id}/disable`);
    return data;
  },
};

export const notificationsApi = {
  list: async (): Promise<AlertNotification[]> => {
    const { data } = await client.get<AlertNotification[]>('/notifications');
    return data;
  },
  unreadCount: async (): Promise<UnreadCount> => {
    const { data } = await client.get<UnreadCount>('/notifications/unread');
    return data;
  },
  markRead: async (id: string): Promise<AlertNotification> => {
    const { data } = await client.post<AlertNotification>(`/notifications/${id}/read`);
    return data;
  },
  markAllRead: async (): Promise<void> => {
    await client.post('/notifications/read-all');
  },
};
