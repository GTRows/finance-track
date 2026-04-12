import client from './client';
import type {
  Portfolio,
  CreatePortfolioRequest,
  UpdatePortfolioRequest,
} from '@/types/portfolio.types';

/** API methods for portfolio CRUD. */
export const portfolioApi = {
  list: async (): Promise<Portfolio[]> => {
    const { data } = await client.get<Portfolio[]>('/portfolios');
    return data;
  },

  get: async (id: string): Promise<Portfolio> => {
    const { data } = await client.get<Portfolio>(`/portfolios/${id}`);
    return data;
  },

  create: async (request: CreatePortfolioRequest): Promise<Portfolio> => {
    const { data } = await client.post<Portfolio>('/portfolios', request);
    return data;
  },

  update: async (id: string, request: UpdatePortfolioRequest): Promise<Portfolio> => {
    const { data } = await client.put<Portfolio>(`/portfolios/${id}`, request);
    return data;
  },

  delete: async (id: string): Promise<void> => {
    await client.delete(`/portfolios/${id}`);
  },
};
