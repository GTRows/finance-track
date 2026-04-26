import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { allocationApi } from '@/api/allocation.api';
import { useAllocation, useSetAllocation } from './useAllocation';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/allocation.api', () => ({
  allocationApi: { get: vi.fn(), set: vi.fn() },
}));

describe('useAllocation hooks', () => {
  beforeEach(() => {
    vi.mocked(allocationApi.get).mockReset();
    vi.mocked(allocationApi.set).mockReset();
  });

  it('useAllocation is disabled without portfolioId', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useAllocation(undefined), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
  });

  it('useAllocation returns the summary', async () => {
    vi.mocked(allocationApi.get).mockResolvedValueOnce({ configured: true, targets: [] } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useAllocation('p1'), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(allocationApi.get).toHaveBeenCalledWith('p1');
  });

  it('useSetAllocation invalidates the allocation cache', async () => {
    vi.mocked(allocationApi.set).mockResolvedValueOnce({} as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useSetAllocation('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ targets: [] } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['allocation', 'p1'] });
  });
});
