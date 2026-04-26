import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { recurringApi } from '@/api/recurring.api';
import {
  useCreateRecurring,
  useDeleteRecurring,
  useRecurringTemplates,
  useRunRecurring,
  useUpdateRecurring,
} from './useRecurring';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/recurring.api', () => ({
  recurringApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    runNow: vi.fn(),
  },
}));

describe('useRecurring hooks', () => {
  beforeEach(() => {
    Object.values(recurringApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useRecurringTemplates returns the list', async () => {
    vi.mocked(recurringApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useRecurringTemplates(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useCreateRecurring invalidates the templates cache', async () => {
    vi.mocked(recurringApi.create).mockResolvedValueOnce({ id: 't1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateRecurring(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ amount: 100 } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['recurring-templates'] });
  });

  it('useUpdateRecurring forwards id and request', async () => {
    vi.mocked(recurringApi.update).mockResolvedValueOnce({ id: 't1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateRecurring(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 't1', req: {} as never });
    });

    expect(recurringApi.update).toHaveBeenCalledWith('t1', {});
  });

  it('useDeleteRecurring passes id', async () => {
    vi.mocked(recurringApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteRecurring(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('t1');
    });

    expect(recurringApi.delete).toHaveBeenCalledWith('t1');
  });

  it('useRunRecurring invalidates templates + budget + dashboard', async () => {
    vi.mocked(recurringApi.runNow).mockResolvedValueOnce({ id: 't1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useRunRecurring(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('t1');
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['recurring-templates'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['budget'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
  });
});
