import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { transactionApi } from '@/api/transaction.api';
import type { RecordTransactionRequest } from '@/types/portfolio.types';

const transactionsKey = (portfolioId: string) => ['portfolios', portfolioId, 'transactions'] as const;
const holdingsKey = (portfolioId: string) => ['portfolios', portfolioId, 'holdings'] as const;

export function useTransactions(portfolioId: string | undefined) {
  return useQuery({
    queryKey: transactionsKey(portfolioId ?? ''),
    queryFn: () => transactionApi.list(portfolioId as string),
    enabled: !!portfolioId,
  });
}

export function useRecordTransaction(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: RecordTransactionRequest) => transactionApi.record(portfolioId, request),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: transactionsKey(portfolioId) });
      qc.invalidateQueries({ queryKey: holdingsKey(portfolioId) });
    },
  });
}

export function useDeleteTransaction(portfolioId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (txnId: string) => transactionApi.delete(portfolioId, txnId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: transactionsKey(portfolioId) });
      qc.invalidateQueries({ queryKey: holdingsKey(portfolioId) });
    },
  });
}
