import client from './client';
import type {
  Bill,
  BillPayment,
  CreateBillRequest,
  PayBillRequest,
  SubscriptionAudit,
} from '@/types/bill.types';

export const billsApi = {
  list: async (): Promise<Bill[]> => {
    const { data } = await client.get<Bill[]>('/bills');
    return data;
  },

  create: async (req: CreateBillRequest): Promise<Bill> => {
    const { data } = await client.post<Bill>('/bills', req);
    return data;
  },

  update: async (id: string, req: CreateBillRequest): Promise<Bill> => {
    const { data } = await client.put<Bill>(`/bills/${id}`, req);
    return data;
  },

  delete: async (id: string): Promise<void> => {
    await client.delete(`/bills/${id}`);
  },

  pay: async (id: string, req: PayBillRequest): Promise<Bill> => {
    const { data } = await client.post<Bill>(`/bills/${id}/pay`, req);
    return data;
  },

  history: async (id: string): Promise<BillPayment[]> => {
    const { data } = await client.get<BillPayment[]>(`/bills/${id}/history`);
    return data;
  },

  markUsed: async (id: string): Promise<Bill> => {
    const { data } = await client.post<Bill>(`/bills/${id}/mark-used`);
    return data;
  },

  audit: async (): Promise<SubscriptionAudit> => {
    const { data } = await client.get<SubscriptionAudit>('/bills/audit');
    return data;
  },
};
