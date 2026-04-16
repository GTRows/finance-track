import type { BudgetTxnType } from './budget.types';

export interface RecurringTemplate {
  id: string;
  txnType: BudgetTxnType;
  amount: number;
  categoryId: string | null;
  categoryName: string | null;
  description: string | null;
  dayOfMonth: number;
  active: boolean;
  lastMaterializedOn: string | null;
  nextDueOn: string;
}

export interface UpsertRecurringRequest {
  txnType: BudgetTxnType;
  amount: number;
  categoryId?: string | null;
  description?: string | null;
  dayOfMonth: number;
  active?: boolean;
}
