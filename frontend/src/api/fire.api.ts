import client from './client';
import type { FireQuery, FireResult } from '@/types/fire.types';

export const fireApi = {
  compute: async (query: FireQuery): Promise<FireResult> => {
    const params: Record<string, number> = {};
    if (query.withdrawalRate != null) params.withdrawalRate = query.withdrawalRate;
    if (query.expectedReturn != null) params.expectedReturn = query.expectedReturn;
    if (query.monthlyContribution != null) params.monthlyContribution = query.monthlyContribution;
    if (query.monthlyExpense != null) params.monthlyExpense = query.monthlyExpense;
    if (query.netWorth != null) params.netWorth = query.netWorth;
    const { data } = await client.get<FireResult>('/fire', { params });
    return data;
  },
};
