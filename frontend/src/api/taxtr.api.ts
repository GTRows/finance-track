import client from './client';
import type { TrTaxReport } from '@/types/tax.types';

export const taxTrApi = {
  async fetch(year?: number | null): Promise<TrTaxReport> {
    const params: Record<string, string> = {};
    if (year != null) params.year = String(year);
    const { data } = await client.get<TrTaxReport>('/reports/tax/tr', { params });
    return data;
  },
};
