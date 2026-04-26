import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { cashFlowApi } from '@/api/cashflow.api';
import {
  useCashFlowBuckets,
  useCashFlowPreview,
  useReplaceCashFlowBuckets,
} from './useCashFlow';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/cashflow.api', () => ({
  cashFlowApi: {
    listBuckets: vi.fn(),
    replaceBuckets: vi.fn(),
    preview: vi.fn(),
  },
}));

describe('useCashFlow hooks', () => {
  beforeEach(() => {
    Object.values(cashFlowApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useCashFlowBuckets returns the list', async () => {
    vi.mocked(cashFlowApi.listBuckets).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useCashFlowBuckets(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useReplaceCashFlowBuckets invalidates buckets', async () => {
    vi.mocked(cashFlowApi.replaceBuckets).mockResolvedValueOnce([] as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useReplaceCashFlowBuckets(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync([] as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['cashflow', 'buckets'] });
  });

  it('useCashFlowPreview invokes the preview endpoint', async () => {
    vi.mocked(cashFlowApi.preview).mockResolvedValueOnce({ buckets: [] } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useCashFlowPreview(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ income: 10000 } as never);
    });

    expect(cashFlowApi.preview).toHaveBeenCalledWith({ income: 10000 });
  });
});
