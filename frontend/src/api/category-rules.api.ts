import client from './client';
import type { CategoryRule, UpsertCategoryRuleRequest } from '@/types/category-rule.types';

export const categoryRulesApi = {
  list: async (): Promise<CategoryRule[]> => {
    const { data } = await client.get<CategoryRule[]>('/budget/category-rules');
    return data;
  },
  create: async (req: UpsertCategoryRuleRequest): Promise<CategoryRule> => {
    const { data } = await client.post<CategoryRule>('/budget/category-rules', req);
    return data;
  },
  update: async (id: string, req: UpsertCategoryRuleRequest): Promise<CategoryRule> => {
    const { data } = await client.put<CategoryRule>(`/budget/category-rules/${id}`, req);
    return data;
  },
  delete: async (id: string): Promise<void> => {
    await client.delete(`/budget/category-rules/${id}`);
  },
};
