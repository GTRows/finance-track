import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { transactionApi } from '@/api/transaction.api';
import {
  useDeleteTransaction,
  useRecordTransaction,
  useTransactions,
} from './useTransactions';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/transaction.api', () => ({
  transactionApi: {
    list: vi.fn(),
    record: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('useTransactions hooks', () => {
  beforeEach(() => {
    Object.values(transactionApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useTransactions is disabled without portfolioId', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useTransactions(undefined), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(transactionApi.list).not.toHaveBeenCalled();
  });

  it('useTransactions calls api.list with portfolio id', async () => {
    vi.mocked(transactionApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useTransactions('p1'), { wrapper: Wrapper });

    await waitFor(() => expect(transactionApi.list).toHaveBeenCalledWith('p1'));
  });

  it('useRecordTransaction invalidates txns + holdings + dashboard', async () => {
    vi.mocked(transactionApi.record).mockResolvedValueOnce({ id: 't1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useRecordTransaction('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ assetId: 'a1' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({
      queryKey: ['portfolios', 'p1', 'transactions'],
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: ['portfolios', 'p1', 'holdings'],
    });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
  });

  it('useDeleteTransaction passes both ids', async () => {
    vi.mocked(transactionApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteTransaction('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('t1');
    });

    expect(transactionApi.delete).toHaveBeenCalledWith('p1', 't1');
  });
});
