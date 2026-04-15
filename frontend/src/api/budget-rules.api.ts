import client from './client';
import type { BudgetRule, CreateBudgetRuleRequest } from '@/types/budget-rule.types';

export const budgetRulesApi = {
  list: async (): Promise<BudgetRule[]> => {
    const { data } = await client.get<BudgetRule[]>('/budget/rules');
    return data;
  },
  create: async (req: CreateBudgetRuleRequest): Promise<BudgetRule> => {
    const { data } = await client.post<BudgetRule>('/budget/rules', req);
    return data;
  },
  delete: async (id: string): Promise<void> => {
    await client.delete(`/budget/rules/${id}`);
  },
};
