import client from './client';
import type { RecordTransactionRequest, Transaction } from '@/types/portfolio.types';

export const transactionApi = {
  list: async (portfolioId: string): Promise<Transaction[]> => {
    const { data } = await client.get<Transaction[]>(`/portfolios/${portfolioId}/transactions`);
    return data;
  },

  record: async (portfolioId: string, request: RecordTransactionRequest): Promise<Transaction> => {
    const { data } = await client.post<Transaction>(`/portfolios/${portfolioId}/transactions`, request);
    return data;
  },

  delete: async (portfolioId: string, txnId: string): Promise<void> => {
    await client.delete(`/portfolios/${portfolioId}/transactions/${txnId}`);
  },
};
