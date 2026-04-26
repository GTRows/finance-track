import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { assetApi } from '@/api/asset.api';
import { useAsset, useAssetHistory } from './useAssetHistory';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/asset.api', () => ({
  assetApi: { get: vi.fn(), history: vi.fn(), list: vi.fn() },
}));

describe('useAsset / useAssetHistory', () => {
  beforeEach(() => {
    vi.mocked(assetApi.get).mockReset();
    vi.mocked(assetApi.history).mockReset();
  });

  it('useAsset is disabled without id', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useAsset(undefined), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
  });

  it('useAsset fetches the asset by id', async () => {
    vi.mocked(assetApi.get).mockResolvedValueOnce({ id: 'a1', symbol: 'BTC' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useAsset('a1'), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(assetApi.get).toHaveBeenCalledWith('a1');
  });

  it('useAssetHistory passes assetId and days', async () => {
    vi.mocked(assetApi.history).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useAssetHistory('a1', 90), { wrapper: Wrapper });
    await waitFor(() => expect(assetApi.history).toHaveBeenCalledWith('a1', 90));
  });
});
