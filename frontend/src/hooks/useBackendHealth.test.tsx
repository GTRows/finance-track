import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import client from '@/api/client';
import { useBackendHealth } from './useBackendHealth';
import { createWrapper } from '@/test-utils/queryWrapper';

describe('useBackendHealth', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('returns true when health endpoint succeeds', async () => {
    vi.spyOn(client, 'get').mockResolvedValueOnce({ status: 200 } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useBackendHealth(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBe(true);
  });

  it('returns false when health endpoint rejects', async () => {
    vi.spyOn(client, 'get').mockRejectedValueOnce(new Error('down'));
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useBackendHealth(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBe(false);
  });
});
