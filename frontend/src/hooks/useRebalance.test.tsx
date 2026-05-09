import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { rebalanceApi } from '@/api/rebalance.api';
import {
  useRebalanceCommit,
  useRebalancePreview,
  useUpdateRebalanceThreshold,
} from './useRebalance';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/rebalance.api', () => ({
  rebalanceApi: { preview: vi.fn(), commit: vi.fn(), updateThreshold: vi.fn() },
}));

describe('useRebalance hooks', () => {
  beforeEach(() => {
    vi.mocked(rebalanceApi.preview).mockReset();
    vi.mocked(rebalanceApi.commit).mockReset();
    vi.mocked(rebalanceApi.updateThreshold).mockReset();
  });

  it('useRebalancePreview calls api.preview with the portfolio id', async () => {
    vi.mocked(rebalanceApi.preview).mockResolvedValueOnce({ proposalId: 'p1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useRebalancePreview('portfolio-1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ accountId: 'acc-1' });
    });

    expect(rebalanceApi.preview).toHaveBeenCalledWith('portfolio-1', { accountId: 'acc-1' });
  });

  it('useRebalanceCommit invalidates dependent caches', async () => {
    vi.mocked(rebalanceApi.commit).mockResolvedValueOnce({
      proposalId: 'p1',
      committedCount: 1,
      transactionIds: ['t1'],
    } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useRebalanceCommit('portfolio-1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({
        proposalId: 'p1',
        accountId: 'acc-1',
        selectedIndices: [0],
      });
    });

    await waitFor(() => {
      expect(invalidate).toHaveBeenCalledWith({
        queryKey: ['portfolios', 'portfolio-1', 'holdings'],
      });
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['transactions', 'portfolio-1'] });
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['allocation', 'portfolio-1'] });
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    });
  });

  it('useUpdateRebalanceThreshold posts the threshold and invalidates settings', async () => {
    vi.mocked(rebalanceApi.updateThreshold).mockResolvedValueOnce({ threshold: 2.5 } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateRebalanceThreshold(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ threshold: 2.5 });
    });

    expect(rebalanceApi.updateThreshold).toHaveBeenCalledWith({ threshold: 2.5 });
    await waitFor(() => {
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['settings'] });
    });
  });

  it('useRebalancePreview surfaces server errors to the caller', async () => {
    vi.mocked(rebalanceApi.preview).mockRejectedValueOnce(new Error('stale'));
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useRebalancePreview('portfolio-1'), { wrapper: Wrapper });
    await expect(
      result.current.mutateAsync({ accountId: 'acc-1' }),
    ).rejects.toThrow('stale');
  });
});
