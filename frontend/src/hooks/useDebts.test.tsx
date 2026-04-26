import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { debtsApi } from '@/api/debts.api';
import {
  useAddDebtPayment,
  useArchiveDebt,
  useCreateDebt,
  useDebtPayments,
  useDebts,
  useDeleteDebtPayment,
  useUpdateDebt,
} from './useDebts';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/debts.api', () => ({
  debtsApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    archive: vi.fn(),
    payments: vi.fn(),
    addPayment: vi.fn(),
    deletePayment: vi.fn(),
  },
}));

describe('useDebts hooks', () => {
  beforeEach(() => {
    Object.values(debtsApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useDebts returns the list', async () => {
    vi.mocked(debtsApi.list).mockResolvedValueOnce([{ id: 'd1' } as never]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDebts(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
  });

  it('useCreateDebt invalidates debts on success', async () => {
    vi.mocked(debtsApi.create).mockResolvedValueOnce({ id: 'd1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateDebt(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ name: 'Loan' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['debts'] });
  });

  it('useUpdateDebt forwards id and request', async () => {
    vi.mocked(debtsApi.update).mockResolvedValueOnce({ id: 'd1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateDebt(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'd1', req: { name: 'X' } as never });
    });

    expect(debtsApi.update).toHaveBeenCalledWith('d1', { name: 'X' });
  });

  it('useArchiveDebt calls archive', async () => {
    vi.mocked(debtsApi.archive).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useArchiveDebt(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('d1');
    });

    expect(debtsApi.archive).toHaveBeenCalledWith('d1');
  });

  it('useDebtPayments is disabled when id is null', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useDebtPayments(null), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(debtsApi.payments).not.toHaveBeenCalled();
  });

  it('useAddDebtPayment invalidates both keys', async () => {
    vi.mocked(debtsApi.addPayment).mockResolvedValueOnce({ id: 'p1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useAddDebtPayment(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'd1', req: {} as never });
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['debts'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['debts', 'd1', 'payments'] });
  });

  it('useDeleteDebtPayment forwards both ids', async () => {
    vi.mocked(debtsApi.deletePayment).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteDebtPayment(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'd1', paymentId: 'p1' });
    });

    expect(debtsApi.deletePayment).toHaveBeenCalledWith('d1', 'p1');
  });
});
