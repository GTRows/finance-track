import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { snapshotApi } from '@/api/snapshot.api';
import { analyticsApi } from '@/api/analytics.api';
import {
  useBenchmarks,
  useCashFlowProjection,
  usePortfolioComparison,
  usePortfolioSnapshotsAggregate,
} from './useAnalytics';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/snapshot.api', () => ({
  snapshotApi: { list: vi.fn() },
}));
vi.mock('@/api/analytics.api', () => ({
  analyticsApi: {
    projectCashFlow: vi.fn(),
    fetchBenchmarks: vi.fn(),
    fetchPortfolioComparison: vi.fn(),
  },
}));

describe('useAnalytics hooks', () => {
  beforeEach(() => {
    vi.mocked(snapshotApi.list).mockReset();
    vi.mocked(analyticsApi.projectCashFlow).mockReset();
    vi.mocked(analyticsApi.fetchBenchmarks).mockReset();
    vi.mocked(analyticsApi.fetchPortfolioComparison).mockReset();
  });

  it('usePortfolioSnapshotsAggregate returns empty when no portfolios', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => usePortfolioSnapshotsAggregate(undefined), { wrapper: Wrapper });

    expect(result.current.data).toEqual([]);
    expect(result.current.isLoading).toBe(false);
  });

  it('usePortfolioSnapshotsAggregate sums values across portfolios by date', async () => {
    vi.mocked(snapshotApi.list).mockImplementation((id: string) =>
      Promise.resolve(
        id === 'p1'
          ? ([{ date: '2026-04-01', totalValueTry: 100, totalCostTry: 80 }] as never)
          : ([{ date: '2026-04-01', totalValueTry: 50, totalCostTry: 40 }] as never),
      ),
    );

    const { Wrapper } = createWrapper();
    const { result } = renderHook(
      () => usePortfolioSnapshotsAggregate([{ id: 'p1' } as never, { id: 'p2' } as never]),
      { wrapper: Wrapper },
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.data).toEqual([
      {
        date: '2026-04-01',
        totalValueTry: 150,
        totalCostTry: 120,
        pnlTry: 30,
        pnlPercent: 30 / 120,
      },
    ]);
  });

  it('useCashFlowProjection passes months and balance', async () => {
    vi.mocked(analyticsApi.projectCashFlow).mockResolvedValueOnce({ months: [] } as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useCashFlowProjection(6, 1000), { wrapper: Wrapper });
    await waitFor(() =>
      expect(analyticsApi.projectCashFlow).toHaveBeenCalledWith(6, 1000),
    );
  });

  it('useBenchmarks passes days param', async () => {
    vi.mocked(analyticsApi.fetchBenchmarks).mockResolvedValueOnce({ series: [] } as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useBenchmarks(90), { wrapper: Wrapper });
    await waitFor(() => expect(analyticsApi.fetchBenchmarks).toHaveBeenCalledWith(90));
  });

  it('usePortfolioComparison forwards ids and date params', async () => {
    vi.mocked(analyticsApi.fetchPortfolioComparison).mockResolvedValueOnce({
      currency: 'TRY',
      series: [],
    } as never);
    const { Wrapper } = createWrapper();

    renderHook(
      () => usePortfolioComparison(['p1', 'p2'], '2026-01-01', '2026-04-01'),
      { wrapper: Wrapper },
    );

    await waitFor(() =>
      expect(analyticsApi.fetchPortfolioComparison).toHaveBeenCalledWith({
        ids: ['p1', 'p2'],
        from: '2026-01-01',
        to: '2026-04-01',
      }),
    );
  });

  it('usePortfolioComparison cache key sort dedupes reordered selections', async () => {
    vi.mocked(analyticsApi.fetchPortfolioComparison).mockResolvedValue({
      currency: 'TRY',
      series: [],
    } as never);
    const { Wrapper, client } = createWrapper();

    const first = renderHook(() => usePortfolioComparison(['b', 'a']), { wrapper: Wrapper });
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));

    // Re-rendering with reversed order should hit the same cache entry inside the same
    // QueryClient — sortedKey makes both orderings share a queryKey.
    renderHook(() => usePortfolioComparison(['a', 'b']), { wrapper: Wrapper });

    const cachedKeys = client
      .getQueryCache()
      .getAll()
      .filter((q) => q.queryKey[0] === 'analytics' && q.queryKey[1] === 'compare');
    expect(cachedKeys).toHaveLength(1);
    expect(cachedKeys[0].queryKey).toEqual(['analytics', 'compare', 'a,b', null, null]);
  });
});
