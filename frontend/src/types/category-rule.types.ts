export type TxnType = 'INCOME' | 'EXPENSE';

export interface CategoryRule {
  id: string;
  pattern: string;
  categoryId: string;
  categoryName: string;
  categoryColor: string | null;
  txnType: TxnType;
  priority: number;
  matchCount: number;
  createdAt: string;
}

export interface UpsertCategoryRuleRequest {
  pattern: string;
  categoryId: string;
  txnType: TxnType;
  priority?: number;
}
