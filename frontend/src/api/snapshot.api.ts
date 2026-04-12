import client from './client';
import type { PortfolioSnapshot } from '@/types/portfolio.types';

/** API methods for reading portfolio value history. */
export const snapshotApi = {
  list: async (portfolioId: string): Promise<PortfolioSnapshot[]> => {
    const { data } = await client.get<PortfolioSnapshot[]>(`/portfolios/${portfolioId}/history`);
    return data;
  },
};
