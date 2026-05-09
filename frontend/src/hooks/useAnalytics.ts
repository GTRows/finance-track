import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query';
import { snapshotApi } from '@/api/snapshot.api';
import {
  analyticsApi,
  type CashFlowProjection,
  type BenchmarkResponse,
  type PortfolioComparisonResponse,
} from '@/api/analytics.api';
import type { Portfolio, PortfolioSnapshot } from '@/types/portfolio.types';

export interface AggregatedSnapshotPoint {
  date: string;
  totalValueTry: number;
  totalCostTry: number;
  pnlTry: number;
  pnlPercent: number | null;
}

interface AnalyticsSnapshotsResult {
  data: AggregatedSnapshotPoint[];
  isLoading: boolean;
  isError: boolean;
}

export function usePortfolioSnapshotsAggregate(
  portfolios: Portfolio[] | undefined
): AnalyticsSnapshotsResult {
  const queries = useQueries({
    queries: (portfolios ?? []).map((p) => ({
      queryKey: ['portfolios', p.id, 'history'] as const,
      queryFn: () => snapshotApi.list(p.id),
      staleTime: 60_000,
    })),
  });

  const isLoading = queries.some((q) => q.isLoading);
  const isError = queries.some((q) => q.isError);

  if (isLoading || isError || !portfolios || portfolios.length === 0) {
    return { data: [], isLoading, isError };
  }

  const bucket = new Map<string, { value: number; cost: number }>();
  queries.forEach((q) => {
    const rows = (q.data ?? []) as PortfolioSnapshot[];
    rows.forEach((row) => {
      const entry = bucket.get(row.date) ?? { value: 0, cost: 0 };
      entry.value += row.totalValueTry ?? 0;
      entry.cost += row.totalCostTry ?? 0;
      bucket.set(row.date, entry);
    });
  });

  const data = Array.from(bucket.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, { value, cost }]) => {
      const pnl = value - cost;
      const pnlPercent = cost > 0 ? pnl / cost : null;
      return {
        date,
        totalValueTry: value,
        totalCostTry: cost,
        pnlTry: pnl,
        pnlPercent,
      };
    });

  return { data, isLoading: false, isError: false };
}

export function useCashFlowProjection(months = 12, startingBalance?: number) {
  return useQuery<CashFlowProjection>({
    queryKey: ['analytics', 'cashFlowProjection', months, startingBalance ?? null],
    queryFn: () => analyticsApi.projectCashFlow(months, startingBalance),
    staleTime: 60_000,
  });
}

export function useBenchmarks(days = 365) {
  return useQuery<BenchmarkResponse>({
    queryKey: ['analytics', 'benchmarks', days],
    queryFn: () => analyticsApi.fetchBenchmarks(days),
    staleTime: 15 * 60_000,
  });
}

/**
 * Fetches a multi-portfolio comparison series. The cache key sorts ids before joining so
 * reordering the selection does not double-fetch.
 */
export function usePortfolioComparison(ids: string[], from?: string, to?: string) {
  const sortedKey = [...ids].sort().join(',');
  return useQuery<PortfolioComparisonResponse>({
    queryKey: ['analytics', 'compare', sortedKey, from ?? null, to ?? null],
    queryFn: () => analyticsApi.fetchPortfolioComparison({ ids, from, to }),
    enabled: ids.length > 0,
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  });
}
