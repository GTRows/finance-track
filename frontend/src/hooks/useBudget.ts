import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { budgetApi } from '@/api/budget.api';
import type { CreateTransactionRequest } from '@/types/budget.types';

const txnKey = (month: string, type?: string) => ['budget', 'transactions', month, type ?? 'ALL'] as const;
const summaryKey = (month: string) => ['budget', 'summary', month] as const;
const categoriesKey = () => ['budget', 'categories'] as const;
const summariesKey = () => ['budget', 'summaries'] as const;

export function useTransactions(month: string, type?: string, page = 0) {
  return useQuery({
    queryKey: [...txnKey(month, type), page],
    queryFn: () => budgetApi.listTransactions(month, type, page),
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
      qc.invalidateQueries({ queryKey: ['budget', 'transactions', month] });
      qc.invalidateQueries({ queryKey: summaryKey(month) });
    },
  });
}

export function useDeleteTransaction(month: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => budgetApi.deleteTransaction(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['budget', 'transactions', month] });
      qc.invalidateQueries({ queryKey: summaryKey(month) });
    },
  });
}

export function useCaptureSnapshot(month: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => budgetApi.captureSnapshot(month),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: summariesKey() });
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
