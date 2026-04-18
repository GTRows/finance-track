import client from './client';
import type { Tag, UpsertTagRequest } from '@/types/tag.types';

export const tagsApi = {
  list: async (): Promise<Tag[]> => {
    const { data } = await client.get<Tag[]>('/tags');
    return data;
  },

  create: async (req: UpsertTagRequest): Promise<Tag> => {
    const { data } = await client.post<Tag>('/tags', req);
    return data;
  },

  update: async (id: string, req: UpsertTagRequest): Promise<Tag> => {
    const { data } = await client.put<Tag>(`/tags/${id}`, req);
    return data;
  },

  remove: async (id: string): Promise<void> => {
    await client.delete(`/tags/${id}`);
  },
};
