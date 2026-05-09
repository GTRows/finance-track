import type { AssetType } from './portfolio.types';

export type RebalanceAction = 'BUY' | 'SELL';

export interface RebalanceSuggestion {
  index: number;
  assetId: string | null;
  symbol: string | null;
  assetType: AssetType;
  action: RebalanceAction;
  quantity: number;
  estimatedPriceTry: number;
  estimatedAmountTry: number;
  currentValueTry: number;
  currentWeightPercent: number;
  targetWeightPercent: number;
  driftPercentBefore: number;
  warning: string | null;
}

export interface RebalancePreview {
  proposalId: string;
  totalValueTry: number;
  accountCashTry: number;
  driftThresholdPercent: number;
  suggestions: RebalanceSuggestion[];
  projectedDriftAfterPercent: number;
  summaryWarnings: string[];
  expiresAt: string;
}

export interface RebalancePreviewRequest {
  accountId: string;
  driftThresholdOverride?: number | null;
}

export interface RebalanceCommitRequest {
  proposalId: string;
  accountId: string;
  selectedIndices: number[];
}

export interface RebalanceCommitResult {
  proposalId: string;
  committedCount: number;
  transactionIds: string[];
}

export interface UpdateRebalanceThresholdRequest {
  threshold: number;
}

export interface UpdateRebalanceThresholdResponse {
  threshold: number;
}
