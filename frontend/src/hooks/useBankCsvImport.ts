import { useMutation, useQueryClient } from '@tanstack/react-query';
import { bankCsvApi } from '@/api/bankcsv.api';
import type { Bank, BankCsvImportSummary } from '@/types/bankCsv.types';

interface ImportArgs {
  file: File;
  bank: Bank;
  accountId: string;
}

/** Mutation for the preview leg -- never persists, returns the summary only. */
export function useBankCsvPreview() {
  return useMutation<BankCsvImportSummary, Error, ImportArgs>({
    mutationFn: (a) => bankCsvApi.preview(a.file, a.bank, a.accountId),
  });
}

/**
 * Mutation for the commit leg. On success, invalidates every cache that an
 * imported transaction can move: budget transactions, account list + totals,
 * dashboard tiles, the emergency-fund tile, and the budget summary.
 */
export function useBankCsvCommit() {
  const qc = useQueryClient();
  return useMutation<BankCsvImportSummary, Error, ImportArgs>({
    mutationFn: (a) => bankCsvApi.commit(a.file, a.bank, a.accountId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['transactions'] });
      void qc.invalidateQueries({ queryKey: ['accounts'] });
      void qc.invalidateQueries({ queryKey: ['accounts', 'totals'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      void qc.invalidateQueries({ queryKey: ['emergencyFund'] });
      void qc.invalidateQueries({ queryKey: ['budgetSummary'] });
    },
  });
}
