import client from './client';
import type {
  Account,
  AccountTotalsResponse,
  CreateAccountRequest,
  UpdateAccountRequest,
} from '@/types/account.types';

/** API methods for account CRUD + totals. */
export const accountsApi = {
  list: async (): Promise<Account[]> => {
    const { data } = await client.get<Account[]>('/accounts');
    return data;
  },

  totals: async (): Promise<AccountTotalsResponse> => {
    const { data } = await client.get<AccountTotalsResponse>('/accounts/totals');
    return data;
  },

  get: async (id: string): Promise<Account> => {
    const { data } = await client.get<Account>(`/accounts/${id}`);
    return data;
  },

  create: async (request: CreateAccountRequest): Promise<Account> => {
    const { data } = await client.post<Account>('/accounts', request);
    return data;
  },

  update: async (id: string, request: UpdateAccountRequest): Promise<Account> => {
    const { data } = await client.put<Account>(`/accounts/${id}`, request);
    return data;
  },

  delete: async (id: string): Promise<void> => {
    await client.delete(`/accounts/${id}`);
  },
};
