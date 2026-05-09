import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { bankCsvApi } from '@/api/bankcsv.api';
import { useBankCsvCommit, useBankCsvPreview } from './useBankCsvImport';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/bankcsv.api', () => ({
  bankCsvApi: {
    preview: vi.fn(),
    commit: vi.fn(),
  },
}));

function makeFile(name: string): File {
  return new File(['header\nrow'], name, { type: 'text/csv' });
}

function emptySummary() {
  return {
    totalRows: 0,
    importedRows: 0,
    skippedRows: 0,
    duplicateRows: 0,
    warningRows: 0,
    rows: [],
  };
}

describe('useBankCsvImport hooks', () => {
  beforeEach(() => {
    vi.mocked(bankCsvApi.preview).mockReset();
    vi.mocked(bankCsvApi.commit).mockReset();
  });

  it('useBankCsvPreview forwards file + bank + accountId to the api', async () => {
    vi.mocked(bankCsvApi.preview).mockResolvedValueOnce(emptySummary());

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useBankCsvPreview(), { wrapper: Wrapper });
    const file = makeFile('garanti.csv');

    await act(async () => {
      await result.current.mutateAsync({ file, bank: 'GARANTI', accountId: 'acc-1' });
    });

    expect(bankCsvApi.preview).toHaveBeenCalledTimes(1);
    expect(bankCsvApi.preview).toHaveBeenCalledWith(file, 'GARANTI', 'acc-1');
  });

  it('useBankCsvCommit invalidates transactions + accounts caches on success', async () => {
    vi.mocked(bankCsvApi.commit).mockResolvedValueOnce({
      ...emptySummary(),
      totalRows: 1,
      importedRows: 1,
    });
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useBankCsvCommit(), { wrapper: Wrapper });
    const file = makeFile('isbank.csv');

    await act(async () => {
      await result.current.mutateAsync({ file, bank: 'ISBANK', accountId: 'acc-2' });
    });

    expect(bankCsvApi.commit).toHaveBeenCalledWith(file, 'ISBANK', 'acc-2');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['transactions'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['accounts', 'totals'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['emergencyFund'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['budgetSummary'] });
  });

  it('useBankCsvCommit propagates the api error and skips invalidation', async () => {
    vi.mocked(bankCsvApi.commit).mockRejectedValueOnce(new Error('boom'));
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useBankCsvCommit(), { wrapper: Wrapper });
    const file = makeFile('akbank.csv');

    await act(async () => {
      await result.current
        .mutateAsync({ file, bank: 'AKBANK', accountId: 'acc-3' })
        .catch(() => undefined);
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('boom');
    expect(invalidate).not.toHaveBeenCalled();
  });
});
