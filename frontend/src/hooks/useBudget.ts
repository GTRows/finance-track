import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { budgetApi } from '@/api/budget.api';
import type { CreateTransactionRequest } from '@/types/budget.types';

const txnKey = (month: string, type?: string, tagId?: string) =>
  ['budget', 'transactions', month, type ?? 'ALL', tagId ?? 'ALL_TAGS'] as const;
const summaryKey = (month: string) => ['budget', 'summary', month] as const;
const categoriesKey = () => ['budget', 'categories'] as const;
const summariesKey = () => ['budget', 'summaries'] as const;

export function useTransactions(month: string, type?: string, page = 0, tagId?: string) {
  return useQuery({
    queryKey: [...txnKey(month, type, tagId), page],
    queryFn: () => budgetApi.listTransactions(month, type, page, 20, tagId),
    enabled: !!month,
  });
}

export function useBudgetSummary(month: string) {
  return useQuery({
    queryKey: summaryKey(month),
    queryFn: () => budgetApi.summary(month),
    enabled: !!month,
  });
}

export function useCategories() {
  return useQuery({
    queryKey: categoriesKey(),
    queryFn: budgetApi.listCategories,
    staleTime: 5 * 60_000,
  });
}

export function useMonthlySummaries() {
  return useQuery({
    queryKey: summariesKey(),
    queryFn: budgetApi.listSummaries,
  });
}

export function useCreateTransaction(month: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateTransactionRequest) => budgetApi.createTransaction(req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['budget', 'transactions', month] });
      void qc.invalidateQueries({ queryKey: summaryKey(month) });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useDeleteTransaction(month: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => budgetApi.deleteTransaction(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['budget', 'transactions', month] });
      void qc.invalidateQueries({ queryKey: summaryKey(month) });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useBulkDeleteTransactions(month: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ids: string[]) => budgetApi.bulkDelete(ids),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['budget', 'transactions', month] });
      void qc.invalidateQueries({ queryKey: summaryKey(month) });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useBulkUpdateTransactions(month: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: Parameters<typeof budgetApi.bulkUpdate>[0]) => budgetApi.bulkUpdate(payload),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['budget', 'transactions', month] });
      void qc.invalidateQueries({ queryKey: summaryKey(month) });
    },
  });
}

export function useCaptureSnapshot(month: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => budgetApi.captureSnapshot(month),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: summariesKey() });
    },
  });
}

export function useCreateIncomeCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: budgetApi.createIncomeCategory,
    onSuccess: () => qc.invalidateQueries({ queryKey: categoriesKey() }),
  });
}

export function useCreateExpenseCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: budgetApi.createExpenseCategory,
    onSuccess: () => qc.invalidateQueries({ queryKey: categoriesKey() }),
  });
}

export function useUpdateExpenseCategory(month?: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: Parameters<typeof budgetApi.updateExpenseCategory>[1] }) =>
      budgetApi.updateExpenseCategory(id, req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: categoriesKey() });
      if (month) void qc.invalidateQueries({ queryKey: summaryKey(month) });
    },
  });
}
