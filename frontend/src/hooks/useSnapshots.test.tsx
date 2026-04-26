import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { snapshotApi } from '@/api/snapshot.api';
import { useSnapshots } from './useSnapshots';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/snapshot.api', () => ({
  snapshotApi: { list: vi.fn() },
}));

describe('useSnapshots', () => {
  beforeEach(() => {
    vi.mocked(snapshotApi.list).mockReset();
  });

  it('is disabled without portfolioId', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useSnapshots(undefined), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
  });

  it('fetches the history with portfolioId', async () => {
    vi.mocked(snapshotApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useSnapshots('p1'), { wrapper: Wrapper });
    await waitFor(() => expect(snapshotApi.list).toHaveBeenCalledWith('p1'));
  });
});
