import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { priceApi } from '@/api/price.api';
import { useRefreshPrices } from './useRefreshPrices';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/price.api', () => ({
  priceApi: { refresh: vi.fn() },
}));

describe('useRefreshPrices', () => {
  beforeEach(() => {
    vi.mocked(priceApi.refresh).mockReset();
  });

  it('invalidates assets, portfolios, dashboard on success', async () => {
    vi.mocked(priceApi.refresh).mockResolvedValueOnce({ cryptoUpdated: 3 } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useRefreshPrices(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['assets'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['portfolios'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
  });
});
