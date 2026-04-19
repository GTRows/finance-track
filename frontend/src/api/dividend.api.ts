import client from './client';
import type { Dividend, RecordDividendRequest } from '@/types/dividend.types';

export const dividendApi = {
  async listForPortfolio(portfolioId: string): Promise<Dividend[]> {
    const { data } = await client.get<Dividend[]>(`/portfolios/${portfolioId}/dividends`);
    return data;
  },
  async record(portfolioId: string, payload: RecordDividendRequest): Promise<Dividend> {
    const { data } = await client.post<Dividend>(`/portfolios/${portfolioId}/dividends`, payload);
    return data;
  },
  async remove(portfolioId: string, dividendId: string): Promise<void> {
    await client.delete(`/portfolios/${portfolioId}/dividends/${dividendId}`);
  },
};
