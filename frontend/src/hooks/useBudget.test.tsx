import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { budgetApi } from '@/api/budget.api';
import {
  useBudgetSummary,
  useBulkDeleteTransactions,
  useCategories,
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
} from './useBudget';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/budget.api', () => ({
  budgetApi: {
    listTransactions: vi.fn(),
    summary: vi.fn(),
    listCategories: vi.fn(),
    listSummaries: vi.fn(),
    createTransaction: vi.fn(),
    deleteTransaction: vi.fn(),
    bulkDelete: vi.fn(),
    bulkUpdate: vi.fn(),
    captureSnapshot: vi.fn(),
    createIncomeCategory: vi.fn(),
    createExpenseCategory: vi.fn(),
    updateExpenseCategory: vi.fn(),
  },
}));

describe('useBudget hooks', () => {
  beforeEach(() => {
    Object.values(budgetApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useTransactions skips fetch when month is empty', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useTransactions(''), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(budgetApi.listTransactions).not.toHaveBeenCalled();
  });

  it('useTransactions calls api with month/type/page/tag', async () => {
    vi.mocked(budgetApi.listTransactions).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useTransactions('2026-04', 'EXPENSE', 1, 'tag-1'), { wrapper: Wrapper });

    await waitFor(() =>
      expect(budgetApi.listTransactions).toHaveBeenCalledWith('2026-04', 'EXPENSE', 1, 20, 'tag-1'),
    );
  });

  it('useBudgetSummary fetches when month is present', async () => {
    vi.mocked(budgetApi.summary).mockResolvedValueOnce({ income: 100 } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useBudgetSummary('2026-04'), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(budgetApi.summary).toHaveBeenCalledWith('2026-04');
  });

  it('useCategories returns categories', async () => {
    vi.mocked(budgetApi.listCategories).mockResolvedValueOnce({ income: [], expense: [] } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useCategories(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useCreateTransaction invalidates txns + summary + dashboard', async () => {
    vi.mocked(budgetApi.createTransaction).mockResolvedValueOnce({ id: 't1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateTransaction('2026-04'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ amount: 100 } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['budget', 'transactions', '2026-04'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['budget', 'summary', '2026-04'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
  });

  it('useDeleteTransaction invalidates the same caches', async () => {
    vi.mocked(budgetApi.deleteTransaction).mockResolvedValueOnce(undefined);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteTransaction('2026-04'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('t1');
    });

    expect(budgetApi.deleteTransaction).toHaveBeenCalledWith('t1');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['budget', 'summary', '2026-04'] });
  });

  it('useBulkDeleteTransactions forwards the id list', async () => {
    vi.mocked(budgetApi.bulkDelete).mockResolvedValueOnce({ affected: 2 } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useBulkDeleteTransactions('2026-04'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync(['a', 'b']);
    });

    expect(budgetApi.bulkDelete).toHaveBeenCalledWith(['a', 'b']);
  });
});
