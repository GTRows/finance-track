import client from './client';
import type {
  RebalanceCommitRequest,
  RebalanceCommitResult,
  RebalancePreview,
  RebalancePreviewRequest,
  UpdateRebalanceThresholdRequest,
  UpdateRebalanceThresholdResponse,
} from '@/types/rebalance.types';

export const rebalanceApi = {
  preview: async (
    portfolioId: string,
    request: RebalancePreviewRequest,
  ): Promise<RebalancePreview> => {
    const { data } = await client.post<RebalancePreview>(
      `/portfolios/${portfolioId}/rebalance/preview`,
      request,
    );
    return data;
  },
  commit: async (
    portfolioId: string,
    request: RebalanceCommitRequest,
  ): Promise<RebalanceCommitResult> => {
    const { data } = await client.post<RebalanceCommitResult>(
      `/portfolios/${portfolioId}/rebalance/commit`,
      request,
    );
    return data;
  },
  updateThreshold: async (
    request: UpdateRebalanceThresholdRequest,
  ): Promise<UpdateRebalanceThresholdResponse> => {
    const { data } = await client.put<UpdateRebalanceThresholdResponse>(
      `/settings/rebalance-threshold`,
      request,
    );
    return data;
  },
};
