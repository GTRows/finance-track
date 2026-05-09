/** Supported TR banks for CSV statement import. Mirrors backend Bank enum. */
export type Bank = 'GARANTI' | 'ISBANK' | 'AKBANK';

/**
 * Per-row preview/commit shape returned by the bank-csv import endpoints. The
 * {@code amount} value is the absolute (always positive) amount; sign lives on
 * {@code inferredType}. {@code fingerprint} is the SHA-256 hex digest used for
 * idempotency. {@code duplicate} is true when the fingerprint already exists for
 * the target account.
 */
export interface BankCsvPreviewRow {
  rowNumber: number;
  date: string | null;
  inferredType: 'INCOME' | 'EXPENSE' | null;
  amount: string | null;
  description: string | null;
  counterparty: string | null;
  matchedCategoryId: string | null;
  matchedCategoryName: string | null;
  fingerprint: string | null;
  warning: string | null;
  duplicate: boolean;
}

/**
 * Aggregate summary returned by both preview and commit endpoints. {@code
 * importedRows} is always 0 for preview; commit increments it as rows persist.
 */
export interface BankCsvImportSummary {
  totalRows: number;
  importedRows: number;
  skippedRows: number;
  duplicateRows: number;
  warningRows: number;
  rows: BankCsvPreviewRow[];
}
