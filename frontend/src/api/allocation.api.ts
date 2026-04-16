import client from './client';
import type { AllocationSummary, SetAllocationRequest } from '@/types/allocation.types';

export const allocationApi = {
  get: async (portfolioId: string): Promise<AllocationSummary> => {
    const { data } = await client.get<AllocationSummary>(`/portfolios/${portfolioId}/allocation`);
    return data;
  },
  set: async (portfolioId: string, req: SetAllocationRequest): Promise<AllocationSummary> => {
    const { data } = await client.put<AllocationSummary>(`/portfolios/${portfolioId}/allocation`, req);
    return data;
  },
};
