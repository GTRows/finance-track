import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { watchlistApi } from '@/api/watchlist.api';
import { useToggleWatchlist, useWatchlist } from './useWatchlist';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/watchlist.api', () => ({
  watchlistApi: {
    list: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
  },
}));

describe('useWatchlist hooks', () => {
  beforeEach(() => {
    vi.mocked(watchlistApi.list).mockReset();
    vi.mocked(watchlistApi.add).mockReset();
    vi.mocked(watchlistApi.remove).mockReset();
  });

  it('useWatchlist exposes the assetId set', async () => {
    vi.mocked(watchlistApi.list).mockResolvedValueOnce([
      { assetId: 'a1' } as never,
      { assetId: 'a2' } as never,
    ]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useWatchlist(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.ids.has('a1')).toBe(true);
    expect(result.current.ids.has('a2')).toBe(true);
    expect(result.current.ids.has('missing')).toBe(false);
  });

  it('useToggleWatchlist removes when watched is true', async () => {
    vi.mocked(watchlistApi.remove).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useToggleWatchlist(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ assetId: 'a1', watched: true });
    });

    expect(watchlistApi.remove).toHaveBeenCalledWith('a1');
    expect(watchlistApi.add).not.toHaveBeenCalled();
  });

  it('useToggleWatchlist adds when watched is false', async () => {
    vi.mocked(watchlistApi.add).mockResolvedValueOnce({ assetId: 'a1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useToggleWatchlist(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ assetId: 'a1', watched: false });
    });

    expect(watchlistApi.add).toHaveBeenCalledWith({ assetId: 'a1' });
    expect(watchlistApi.remove).not.toHaveBeenCalled();
  });
});
