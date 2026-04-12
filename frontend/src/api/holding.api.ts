import client from './client';
import type { Holding, AddHoldingRequest } from '@/types/portfolio.types';

/** API methods for managing holdings inside a portfolio. */
export const holdingApi = {
  list: async (portfolioId: string): Promise<Holding[]> => {
    const { data } = await client.get<Holding[]>(`/portfolios/${portfolioId}/holdings`);
    return data;
  },

  add: async (portfolioId: string, request: AddHoldingRequest): Promise<Holding> => {
    const { data } = await client.post<Holding>(`/portfolios/${portfolioId}/holdings`, request);
    return data;
  },

  delete: async (portfolioId: string, holdingId: string): Promise<void> => {
    await client.delete(`/portfolios/${portfolioId}/holdings/${holdingId}`);
  },
};
