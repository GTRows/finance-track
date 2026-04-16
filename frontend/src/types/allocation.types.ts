import type { AssetType } from './portfolio.types';

export interface AllocationRow {
  assetType: AssetType;
  targetPercent: number;
  actualPercent: number;
  actualValueTry: number;
  driftPercent: number;
  driftValueTry: number;
}

export interface AllocationSummary {
  totalValueTry: number;
  configured: boolean;
  rows: AllocationRow[];
}

export interface AllocationTargetInput {
  assetType: AssetType;
  targetPercent: number;
}

export interface SetAllocationRequest {
  targets: AllocationTargetInput[];
}
