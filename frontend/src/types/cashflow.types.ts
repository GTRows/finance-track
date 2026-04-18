export interface CashFlowBucket {
  id: string;
  name: string;
  percent: number;
  categoryId: string | null;
  ordinal: number;
}

export interface CashFlowBucketInput {
  name: string;
  percent: number;
  categoryId?: string | null;
}

export interface CashFlowPreviewRequest {
  income: number;
  obligations: number;
}

export interface AllocatedBucket {
  name: string;
  categoryId: string | null;
  percent: number;
  amount: number;
}

export interface CashFlowPreview {
  income: number;
  obligations: number;
  discretionary: number;
  assigned: number;
  unassigned: number;
  buckets: AllocatedBucket[];
}
