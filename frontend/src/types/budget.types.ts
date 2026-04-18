/** Budget transaction types. */
export type BudgetTxnType = 'INCOME' | 'EXPENSE';

/** Income or expense category. */
export interface Category {
  id: string;
  name: string;
  icon: string;
  color: string;
  budgetAmount?: number;
  rolloverEnabled: boolean;
}

/** Tag embedded on a transaction response. */
export interface TransactionTagRef {
  id: string;
  name: string;
  color: string | null;
}

/** Budget transaction entry returned by the API. */
export interface BudgetTransaction {
  id: string;
  txnType: BudgetTxnType;
  amount: number;
  currency: string;
  originalAmount: number | null;
  originalCurrency: string | null;
  categoryId: string | null;
  categoryName: string | null;
  categoryColor: string | null;
  description: string | null;
  txnDate: string;
  recurring: boolean;
  tags: TransactionTagRef[] | null;
  hasReceipt: boolean;
  createdAt: string;
}

/** Request body for creating/updating a transaction. */
export interface CreateTransactionRequest {
  txnType: BudgetTxnType;
  amount: number;
  currency?: string;
  categoryId?: string;
  description?: string;
  txnDate: string;
  isRecurring: boolean;
  tagIds?: string[];
}

/** Monthly budget summary (live, computed from transactions). */
export interface BudgetSummary {
  period: string;
  totalIncome: number;
  totalExpense: number;
  net: number;
  savingsRate: number;
  incomeByCategory: CategoryAmount[];
  expenseByCategory: CategoryAmount[];
}

export interface CategoryAmount {
  categoryId: string | null;
  categoryName: string;
  categoryColor: string | null;
  amount: number;
  percent: number;
  baseBudget: number | null;
  rolloverAmount: number | null;
  effectiveBudget: number | null;
}

/** Monthly summary log (captured snapshot). */
export interface MonthlySummary {
  period: string;
  totalIncome: number;
  totalExpense: number;
  net: number;
  savingsRate: number;
  notes: string | null;
  createdAt: string;
}

/** Categories response from /budget/categories. */
export interface CategoriesData {
  income: Category[];
  expense: Category[];
}

/** Request body for creating/updating a category. */
export interface CreateCategoryRequest {
  name: string;
  icon?: string;
  color?: string;
  budgetAmount?: number;
  rolloverEnabled?: boolean;
}

/** Paginated API response. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
