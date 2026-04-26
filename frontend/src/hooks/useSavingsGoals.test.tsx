import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { savingsApi } from '@/api/savings.api';
import {
  useAddSavingsContribution,
  useArchiveSavingsGoal,
  useCreateSavingsGoal,
  useDeleteSavingsContribution,
  useSavingsContributions,
  useSavingsGoals,
  useUpdateSavingsGoal,
} from './useSavingsGoals';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/savings.api', () => ({
  savingsApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    archive: vi.fn(),
    contributions: vi.fn(),
    addContribution: vi.fn(),
    deleteContribution: vi.fn(),
  },
}));

describe('useSavingsGoals hooks', () => {
  beforeEach(() => {
    Object.values(savingsApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useSavingsGoals returns the list', async () => {
    vi.mocked(savingsApi.list).mockResolvedValueOnce([{ id: 'g1' } as never]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useSavingsGoals(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useCreateSavingsGoal invalidates goals on success', async () => {
    vi.mocked(savingsApi.create).mockResolvedValueOnce({ id: 'g1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateSavingsGoal(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ name: 'Travel' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['savings', 'goals'] });
  });

  it('useUpdateSavingsGoal forwards id and request', async () => {
    vi.mocked(savingsApi.update).mockResolvedValueOnce({ id: 'g1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateSavingsGoal(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'g1', req: { name: 'X' } as never });
    });

    expect(savingsApi.update).toHaveBeenCalledWith('g1', { name: 'X' });
  });

  it('useArchiveSavingsGoal calls archive', async () => {
    vi.mocked(savingsApi.archive).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useArchiveSavingsGoal(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('g1');
    });

    expect(savingsApi.archive).toHaveBeenCalledWith('g1');
  });

  it('useSavingsContributions is disabled when id is null', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useSavingsContributions(null), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
  });

  it('useAddSavingsContribution invalidates both keys', async () => {
    vi.mocked(savingsApi.addContribution).mockResolvedValueOnce({ id: 'c1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useAddSavingsContribution(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'g1', req: {} as never });
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['savings', 'goals'] });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: ['savings', 'goals', 'g1', 'contributions'],
    });
  });

  it('useDeleteSavingsContribution forwards both ids', async () => {
    vi.mocked(savingsApi.deleteContribution).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteSavingsContribution(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'g1', contributionId: 'c1' });
    });

    expect(savingsApi.deleteContribution).toHaveBeenCalledWith('g1', 'c1');
  });
});
