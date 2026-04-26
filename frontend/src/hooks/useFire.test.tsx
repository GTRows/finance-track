import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { fireApi } from '@/api/fire.api';
import { useFire } from './useFire';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/fire.api', () => ({
  fireApi: { compute: vi.fn() },
}));

describe('useFire', () => {
  beforeEach(() => {
    vi.mocked(fireApi.compute).mockReset();
  });

  it('passes the query through to the api', async () => {
    vi.mocked(fireApi.compute).mockResolvedValueOnce({ targetNumber: 1000000 } as never);
    const { Wrapper } = createWrapper();

    const query = { withdrawalRate: 0.04 } as never;
    renderHook(() => useFire(query), { wrapper: Wrapper });

    await waitFor(() => expect(fireApi.compute).toHaveBeenCalledWith(query));
  });
});
