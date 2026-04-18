import client from './client';
import type {
  Debt,
  DebtPayment,
  DebtPaymentRequest,
  UpsertDebtRequest,
} from '@/types/debt.types';

export const debtsApi = {
  list: async (): Promise<Debt[]> => {
    const { data } = await client.get<Debt[]>('/debts');
    return data;
  },

  create: async (req: UpsertDebtRequest): Promise<Debt> => {
    const { data } = await client.post<Debt>('/debts', req);
    return data;
  },

  update: async (id: string, req: UpsertDebtRequest): Promise<Debt> => {
    const { data } = await client.put<Debt>(`/debts/${id}`, req);
    return data;
  },

  archive: async (id: string): Promise<void> => {
    await client.delete(`/debts/${id}`);
  },

  payments: async (id: string): Promise<DebtPayment[]> => {
    const { data } = await client.get<DebtPayment[]>(`/debts/${id}/payments`);
    return data;
  },

  addPayment: async (id: string, req: DebtPaymentRequest): Promise<DebtPayment> => {
    const { data } = await client.post<DebtPayment>(`/debts/${id}/payments`, req);
    return data;
  },

  deletePayment: async (id: string, paymentId: string): Promise<void> => {
    await client.delete(`/debts/${id}/payments/${paymentId}`);
  },
};
