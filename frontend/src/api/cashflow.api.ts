import client from './client';
import type {
  CashFlowBucket,
  CashFlowBucketInput,
  CashFlowPreview,
  CashFlowPreviewRequest,
} from '@/types/cashflow.types';

export const cashFlowApi = {
  listBuckets: async (): Promise<CashFlowBucket[]> => {
    const { data } = await client.get<CashFlowBucket[]>('/budget/allocation/buckets');
    return data;
  },

  replaceBuckets: async (buckets: CashFlowBucketInput[]): Promise<CashFlowBucket[]> => {
    const { data } = await client.put<CashFlowBucket[]>('/budget/allocation/buckets', {
      buckets,
    });
    return data;
  },

  preview: async (req: CashFlowPreviewRequest): Promise<CashFlowPreview> => {
    const { data } = await client.post<CashFlowPreview>('/budget/allocation/preview', req);
    return data;
  },
};
