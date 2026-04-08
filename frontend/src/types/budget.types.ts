/** Budget transaction types. */
export type BudgetTxnType = 'INCOME' | 'EXPENSE';

/** Income or expense category. */
export interface Category {
  id: string;
  name: string;
  icon: string;
  color: string;
  budgetAmount?: number;
}

/** Budget transaction entry. */
export interface BudgetTransaction {
  id: string;
  txnType: BudgetTxnType;
  amount: number;
  currency: string;
  categoryId: string;
  description: string;
  notes?: string;
  txnDate: string;
  isRecurring: boolean;
  tags: string[];
}

/** Monthly budget summary. */
export interface MonthlySummary {
  period: string;
  totalIncome: number;
  totalExpense: number;
  net: number;
  savingsRate: number;
}
