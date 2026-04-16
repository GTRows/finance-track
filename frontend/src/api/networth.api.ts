import client from './client';
import type {
  NetWorthEvent,
  NetWorthTimeline,
  UpsertEventRequest,
} from '@/types/networth.types';

export const netWorthApi = {
  timeline: async (): Promise<NetWorthTimeline> => {
    const { data } = await client.get<NetWorthTimeline>('/net-worth');
    return data;
  },

  createEvent: async (req: UpsertEventRequest): Promise<NetWorthEvent> => {
    const { data } = await client.post<NetWorthEvent>('/net-worth/events', req);
    return data;
  },

  updateEvent: async (id: string, req: UpsertEventRequest): Promise<NetWorthEvent> => {
    const { data } = await client.put<NetWorthEvent>(`/net-worth/events/${id}`, req);
    return data;
  },

  deleteEvent: async (id: string): Promise<void> => {
    await client.delete(`/net-worth/events/${id}`);
  },
};
