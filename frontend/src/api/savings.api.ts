import client from './client';
import type {
  SavingsContribution,
  SavingsContributionRequest,
  SavingsGoal,
  UpsertSavingsGoalRequest,
} from '@/types/savings.types';

export const savingsApi = {
  list: async (): Promise<SavingsGoal[]> => {
    const { data } = await client.get<SavingsGoal[]>('/savings/goals');
    return data;
  },

  create: async (req: UpsertSavingsGoalRequest): Promise<SavingsGoal> => {
    const { data } = await client.post<SavingsGoal>('/savings/goals', req);
    return data;
  },

  update: async (id: string, req: UpsertSavingsGoalRequest): Promise<SavingsGoal> => {
    const { data } = await client.put<SavingsGoal>(`/savings/goals/${id}`, req);
    return data;
  },

  archive: async (id: string): Promise<void> => {
    await client.delete(`/savings/goals/${id}`);
  },

  contributions: async (id: string): Promise<SavingsContribution[]> => {
    const { data } = await client.get<SavingsContribution[]>(`/savings/goals/${id}/contributions`);
    return data;
  },

  addContribution: async (
    id: string,
    req: SavingsContributionRequest
  ): Promise<SavingsContribution> => {
    const { data } = await client.post<SavingsContribution>(
      `/savings/goals/${id}/contributions`,
      req
    );
    return data;
  },

  deleteContribution: async (id: string, contributionId: string): Promise<void> => {
    await client.delete(`/savings/goals/${id}/contributions/${contributionId}`);
  },
};
