import client from './client';
import type { Bank, BankCsvImportSummary } from '@/types/bankCsv.types';

function buildForm(file: File, bank: Bank, accountId: string): FormData {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('bank', bank);
  fd.append('accountId', accountId);
  return fd;
}

/** API methods for the TR bank CSV statement importer. */
export const bankCsvApi = {
  preview: async (file: File, bank: Bank, accountId: string): Promise<BankCsvImportSummary> => {
    const { data } = await client.post<BankCsvImportSummary>(
      '/import/bank-csv/preview',
      buildForm(file, bank, accountId),
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
  },

  commit: async (file: File, bank: Bank, accountId: string): Promise<BankCsvImportSummary> => {
    const { data } = await client.post<BankCsvImportSummary>(
      '/import/bank-csv/commit',
      buildForm(file, bank, accountId),
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
  },
};
