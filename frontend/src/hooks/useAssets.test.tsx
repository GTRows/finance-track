import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { assetApi } from '@/api/asset.api';
import { useAssets } from './useAssets';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/asset.api', () => ({
  assetApi: { list: vi.fn(), get: vi.fn(), history: vi.fn() },
}));

describe('useAssets', () => {
  beforeEach(() => {
    vi.mocked(assetApi.list).mockReset();
  });

  it('fetches all assets when no type filter', async () => {
    vi.mocked(assetApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useAssets(), { wrapper: Wrapper });
    await waitFor(() => expect(assetApi.list).toHaveBeenCalledWith(undefined));
  });

  it('passes the type filter through', async () => {
    vi.mocked(assetApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useAssets('CRYPTO' as never), { wrapper: Wrapper });
    await waitFor(() => expect(assetApi.list).toHaveBeenCalledWith('CRYPTO'));
  });
});
