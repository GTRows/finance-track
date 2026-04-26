import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { alertsApi, notificationsApi } from '@/api/alerts.api';
import {
  useAlerts,
  useCreateAlert,
  useDeleteAlert,
  useMarkAllRead,
  useMarkRead,
  useNotifications,
  useUnreadCount,
} from './useAlerts';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/alerts.api', () => ({
  alertsApi: { list: vi.fn(), create: vi.fn(), delete: vi.fn() },
  notificationsApi: {
    list: vi.fn(),
    unreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  },
}));

describe('useAlerts hooks', () => {
  beforeEach(() => {
    Object.values(alertsApi).forEach((fn) => vi.mocked(fn).mockReset());
    Object.values(notificationsApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useAlerts returns the list', async () => {
    vi.mocked(alertsApi.list).mockResolvedValueOnce([{ id: 'a1' } as never]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useAlerts(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
  });

  it('useCreateAlert invalidates alerts on success', async () => {
    vi.mocked(alertsApi.create).mockResolvedValueOnce({ id: 'a1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateAlert(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ assetId: 'x' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['alerts'] });
  });

  it('useDeleteAlert invalidates alerts on success', async () => {
    vi.mocked(alertsApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteAlert(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('a1');
    });

    expect(alertsApi.delete).toHaveBeenCalledWith('a1');
  });

  it('useNotifications returns the list', async () => {
    vi.mocked(notificationsApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useNotifications(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useUnreadCount returns count payload', async () => {
    vi.mocked(notificationsApi.unreadCount).mockResolvedValueOnce({ count: 3 } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUnreadCount(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.count).toBe(3);
  });

  it('useMarkRead invalidates notifications + unread', async () => {
    vi.mocked(notificationsApi.markRead).mockResolvedValueOnce({ id: 'n1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useMarkRead(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('n1');
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['notifications'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['notifications', 'unread'] });
  });

  it('useMarkAllRead invalidates both keys', async () => {
    vi.mocked(notificationsApi.markAllRead).mockResolvedValueOnce(undefined);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useMarkAllRead(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['notifications', 'unread'] });
  });
});
