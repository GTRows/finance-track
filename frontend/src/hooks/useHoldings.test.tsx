import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { holdingApi } from '@/api/holding.api';
import {
  useAddHolding,
  useDeleteHolding,
  useHoldings,
  useToggleHoldingPin,
} from './useHoldings';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/holding.api', () => ({
  holdingApi: {
    list: vi.fn(),
    add: vi.fn(),
    delete: vi.fn(),
    togglePin: vi.fn(),
  },
}));

describe('useHoldings hooks', () => {
  beforeEach(() => {
    Object.values(holdingApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useHoldings is disabled when no portfolioId', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useHoldings(undefined), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(holdingApi.list).not.toHaveBeenCalled();
  });

  it('useHoldings returns the list', async () => {
    vi.mocked(holdingApi.list).mockResolvedValueOnce([
      { id: 'h1', pinned: false } as never,
    ]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useHoldings('p1'), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
  });

  it('useAddHolding invalidates holdings + dashboard on success', async () => {
    vi.mocked(holdingApi.add).mockResolvedValueOnce({ id: 'h2' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useAddHolding('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ assetId: 'a1', quantity: 10 } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['portfolios', 'p1', 'holdings'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
  });

  it('useDeleteHolding forwards the holding id', async () => {
    vi.mocked(holdingApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteHolding('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('h1');
    });

    expect(holdingApi.delete).toHaveBeenCalledWith('p1', 'h1');
  });

  it('useToggleHoldingPin optimistically flips the pinned flag', async () => {
    const { Wrapper, client } = createWrapper();
    client.setQueryData(['portfolios', 'p1', 'holdings'], [
      { id: 'h1', pinned: false },
      { id: 'h2', pinned: true },
    ]);
    vi.mocked(holdingApi.togglePin).mockResolvedValueOnce({ id: 'h1', pinned: true } as never);

    const { result } = renderHook(() => useToggleHoldingPin('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('h1');
    });

    expect(holdingApi.togglePin).toHaveBeenCalledWith('p1', 'h1');
  });

  it('useToggleHoldingPin rolls back on api error', async () => {
    const { Wrapper, client } = createWrapper();
    const initial = [{ id: 'h1', pinned: false }];
    client.setQueryData(['portfolios', 'p1', 'holdings'], initial);
    vi.mocked(holdingApi.togglePin).mockRejectedValueOnce(new Error('nope'));

    const { result } = renderHook(() => useToggleHoldingPin('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('h1').catch(() => undefined);
    });

    const after = client.getQueryData(['portfolios', 'p1', 'holdings']);
    expect(after).toEqual(initial);
  });
});
