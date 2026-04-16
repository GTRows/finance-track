import client from './client';
import type { RecurringTemplate, UpsertRecurringRequest } from '@/types/recurring.types';

export const recurringApi = {
  list: async (): Promise<RecurringTemplate[]> => {
    const { data } = await client.get<RecurringTemplate[]>('/budget/recurring');
    return data;
  },
  create: async (req: UpsertRecurringRequest): Promise<RecurringTemplate> => {
    const { data } = await client.post<RecurringTemplate>('/budget/recurring', req);
    return data;
  },
  update: async (id: string, req: UpsertRecurringRequest): Promise<RecurringTemplate> => {
    const { data } = await client.put<RecurringTemplate>(`/budget/recurring/${id}`, req);
    return data;
  },
  delete: async (id: string): Promise<void> => {
    await client.delete(`/budget/recurring/${id}`);
  },
  runNow: async (id: string): Promise<RecurringTemplate> => {
    const { data } = await client.post<RecurringTemplate>(`/budget/recurring/${id}/run-now`);
    return data;
  },
};
