import client from './client';
import type {
  BudgetTransaction,
  BudgetSummary,
  MonthlySummary,
  CategoriesData,
  Category,
  CreateTransactionRequest,
  CreateCategoryRequest,
  PageResponse,
} from '@/types/budget.types';

export const budgetApi = {
  // -- Transactions --

  listTransactions: async (
    month: string,
    type?: string,
    page = 0,
    size = 20,
    tagId?: string
  ): Promise<PageResponse<BudgetTransaction>> => {
    const params: Record<string, string | number> = { month, page, size };
    if (type) params.type = type;
    if (tagId) params.tagId = tagId;
    const { data } = await client.get<PageResponse<BudgetTransaction>>('/budget/transactions', { params });
    return data;
  },

  createTransaction: async (req: CreateTransactionRequest): Promise<BudgetTransaction> => {
    const { data } = await client.post<BudgetTransaction>('/budget/transactions', req);
    return data;
  },

  updateTransaction: async (id: string, req: CreateTransactionRequest): Promise<BudgetTransaction> => {
    const { data } = await client.put<BudgetTransaction>(`/budget/transactions/${id}`, req);
    return data;
  },

  deleteTransaction: async (id: string): Promise<void> => {
    await client.delete(`/budget/transactions/${id}`);
  },

  bulkDelete: async (ids: string[]): Promise<{ affected: number }> => {
    const { data } = await client.post<{ affected: number }>('/budget/transactions/bulk-delete', { ids });
    return data;
  },

  bulkUpdate: async (payload: {
    ids: string[];
    categoryId?: string;
    clearCategory?: boolean;
    addTagIds?: string[];
    removeTagIds?: string[];
  }): Promise<{ affected: number }> => {
    const { data } = await client.post<{ affected: number }>('/budget/transactions/bulk-update', payload);
    return data;
  },

  // -- Summary --

  summary: async (month: string): Promise<BudgetSummary> => {
    const { data } = await client.get<BudgetSummary>('/budget/summary', { params: { month } });
    return data;
  },

  // -- Monthly logs --

  listSummaries: async (): Promise<MonthlySummary[]> => {
    const { data } = await client.get<MonthlySummary[]>('/budget/summaries');
    return data;
  },

  captureSnapshot: async (period: string): Promise<MonthlySummary> => {
    const { data } = await client.post<MonthlySummary>(`/budget/summaries/${period}/snapshot`);
    return data;
  },

  // -- Categories --

  listCategories: async (): Promise<CategoriesData> => {
    const { data } = await client.get<CategoriesData>('/budget/categories');
    return data;
  },

  createIncomeCategory: async (req: CreateCategoryRequest): Promise<Category> => {
    const { data } = await client.post<Category>('/budget/categories/income', req);
    return data;
  },

  createExpenseCategory: async (req: CreateCategoryRequest): Promise<Category> => {
    const { data } = await client.post<Category>('/budget/categories/expense', req);
    return data;
  },

  updateIncomeCategory: async (id: string, req: CreateCategoryRequest): Promise<Category> => {
    const { data } = await client.put<Category>(`/budget/categories/income/${id}`, req);
    return data;
  },

  updateExpenseCategory: async (id: string, req: CreateCategoryRequest): Promise<Category> => {
    const { data } = await client.put<Category>(`/budget/categories/expense/${id}`, req);
    return data;
  },

  deleteIncomeCategory: async (id: string): Promise<void> => {
    await client.delete(`/budget/categories/income/${id}`);
  },

  deleteExpenseCategory: async (id: string): Promise<void> => {
    await client.delete(`/budget/categories/expense/${id}`);
  },
};
