import client from './client';
import type { RiskMetrics } from '@/types/risk.types';

export const riskApi = {
  get: async (portfolioId: string, riskFreeRate?: number): Promise<RiskMetrics> => {
    const { data } = await client.get<RiskMetrics>(`/portfolios/${portfolioId}/risk`, {
      params: riskFreeRate != null ? { riskFreeRate } : undefined,
    });
    return data;
  },
};
