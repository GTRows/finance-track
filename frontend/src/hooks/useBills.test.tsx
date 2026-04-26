import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { billsApi } from '@/api/bills.api';
import {
  useBills,
  useCreateBill,
  useDeleteBill,
  useMarkBillUsed,
  usePayBill,
  useSubscriptionAudit,
} from './useBills';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/bills.api', () => ({
  billsApi: {
    list: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
    pay: vi.fn(),
    audit: vi.fn(),
    markUsed: vi.fn(),
  },
}));

describe('useBills hooks', () => {
  beforeEach(() => {
    Object.values(billsApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useBills returns the list', async () => {
    vi.mocked(billsApi.list).mockResolvedValueOnce([{ id: 'b1' } as never]);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useBills(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
  });

  it('useCreateBill invalidates bills + dashboard on success', async () => {
    vi.mocked(billsApi.create).mockResolvedValueOnce({ id: 'b1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateBill(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ name: 'Rent' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['bills'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
  });

  it('useDeleteBill calls delete with id', async () => {
    vi.mocked(billsApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteBill(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('b1');
    });

    expect(billsApi.delete).toHaveBeenCalledWith('b1');
  });

  it('usePayBill passes id and request through', async () => {
    vi.mocked(billsApi.pay).mockResolvedValueOnce({ id: 'b1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => usePayBill(), { wrapper: Wrapper });
    const req = { paidAt: '2026-04-01' } as never;
    await act(async () => {
      await result.current.mutateAsync({ id: 'b1', req });
    });

    expect(billsApi.pay).toHaveBeenCalledWith('b1', req);
  });

  it('useSubscriptionAudit returns audit payload', async () => {
    vi.mocked(billsApi.audit).mockResolvedValueOnce({
      totalMonthlySpend: 100,
      potentialMonthlySavings: 10,
      candidateCount: 1,
      candidates: [],
    } as never);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useSubscriptionAudit(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.candidateCount).toBe(1);
  });

  it('useMarkBillUsed invalidates audit cache', async () => {
    vi.mocked(billsApi.markUsed).mockResolvedValueOnce({ id: 'b1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useMarkBillUsed(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('b1');
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['bills', 'audit'] });
  });
});
