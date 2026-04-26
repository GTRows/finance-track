import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { tagsApi } from '@/api/tags.api';
import {
  useCreateTag,
  useDeleteTag,
  useTags,
  useUpdateTag,
} from './useTags';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/tags.api', () => ({
  tagsApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}));

describe('useTags hooks', () => {
  beforeEach(() => {
    Object.values(tagsApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useTags returns the list', async () => {
    vi.mocked(tagsApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useTags(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useCreateTag invalidates tags + budget transactions', async () => {
    vi.mocked(tagsApi.create).mockResolvedValueOnce({ id: 't1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateTag(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ name: 'Travel' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['tags'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['budget', 'transactions'] });
  });

  it('useUpdateTag forwards id and request', async () => {
    vi.mocked(tagsApi.update).mockResolvedValueOnce({ id: 't1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateTag(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 't1', req: { name: 'X' } as never });
    });

    expect(tagsApi.update).toHaveBeenCalledWith('t1', { name: 'X' });
  });

  it('useDeleteTag passes id', async () => {
    vi.mocked(tagsApi.remove).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteTag(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('t1');
    });

    expect(tagsApi.remove).toHaveBeenCalledWith('t1');
  });
});
