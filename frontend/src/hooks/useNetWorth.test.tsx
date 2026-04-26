import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { netWorthApi } from '@/api/networth.api';
import {
  useCreateNetWorthEvent,
  useDeleteNetWorthEvent,
  useNetWorth,
  useUpdateNetWorthEvent,
} from './useNetWorth';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/networth.api', () => ({
  netWorthApi: {
    timeline: vi.fn(),
    createEvent: vi.fn(),
    updateEvent: vi.fn(),
    deleteEvent: vi.fn(),
  },
}));

describe('useNetWorth hooks', () => {
  beforeEach(() => {
    Object.values(netWorthApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useNetWorth returns the timeline', async () => {
    vi.mocked(netWorthApi.timeline).mockResolvedValueOnce({ series: [], events: [] } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useNetWorth(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useCreateNetWorthEvent invalidates timeline', async () => {
    vi.mocked(netWorthApi.createEvent).mockResolvedValueOnce({ id: 'e1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateNetWorthEvent(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ eventDate: '2026-04-01' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['net-worth', 'timeline'] });
  });

  it('useUpdateNetWorthEvent passes id and req', async () => {
    vi.mocked(netWorthApi.updateEvent).mockResolvedValueOnce({ id: 'e1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateNetWorthEvent(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'e1', req: {} as never });
    });

    expect(netWorthApi.updateEvent).toHaveBeenCalledWith('e1', {});
  });

  it('useDeleteNetWorthEvent passes id', async () => {
    vi.mocked(netWorthApi.deleteEvent).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteNetWorthEvent(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('e1');
    });

    expect(netWorthApi.deleteEvent).toHaveBeenCalledWith('e1');
  });
});
