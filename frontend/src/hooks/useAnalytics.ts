import {
  keepPreviousData,
  useMutation,
  useQueries,
  useQuery,
} from '@tanstack/react-query';
import { snapshotApi } from '@/api/snapshot.api';
import { holdingApi } from '@/api/holding.api';
import {
  analyticsApi,
  type CashFlowProjection,
  type BenchmarkResponse,
  type CorrelationMatrixResponse,
  type CorrelationMethodLiteral,
  type MonteCarloDefaultsResponse,
  type MonteCarloRequest,
  type MonteCarloResponse,
  type PortfolioComparisonResponse,
} from '@/api/analytics.api';
import type { Holding, Portfolio, PortfolioSnapshot } from '@/types/portfolio.types';

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

/** Single asset entry as surfaced to the correlation picker. */
export interface HeldAssetSummary {
  assetId: string;
  symbol: string;
  name: string;
  type: string;
}

interface UseHeldAssetsResult {
  data: HeldAssetSummary[];
  isLoading: boolean;
  isError: boolean;
}

/**
 * Aggregates the unique asset universe across the operator's active portfolios. Composed from
 * {@link useQueries} over each portfolio's holdings endpoint so the correlation picker only shows
 * assets the operator currently or recently held — not the entire global asset master.
 */
export function useHeldAssets(portfolios: Portfolio[] | undefined): UseHeldAssetsResult {
  const list = portfolios ?? [];
  const queries = useQueries({
    queries: list.map((p) => ({
      queryKey: ['holdings', p.id] as const,
      queryFn: () => holdingApi.list(p.id),
      staleTime: 60_000,
    })),
  });

  const isLoading = queries.some((q) => q.isLoading);
  const isError = queries.some((q) => q.isError);

  if (isLoading || isError || list.length === 0) {
    return { data: [], isLoading, isError };
  }

  const seen = new Map<string, HeldAssetSummary>();
  queries.forEach((q) => {
    const rows = (q.data ?? []) as Holding[];
    for (const row of rows) {
      if (seen.has(row.assetId)) continue;
      seen.set(row.assetId, {
        assetId: row.assetId,
        symbol: row.assetSymbol,
        name: row.assetName,
        type: row.assetType,
      });
    }
  });

  const data = Array.from(seen.values()).sort((a, b) => a.symbol.localeCompare(b.symbol));
  return { data, isLoading: false, isError: false };
}

/**
 * Fetches an asset correlation matrix. Cache key sorts the asset ids so reordering the selection
 * shares an entry with the prior fetch; flipping the method between Pearson and Spearman bumps
 * the cache key (the server keys the same way).
 */
export function useCorrelationMatrix(
  assetIds: string[],
  from?: string,
  to?: string,
  method: CorrelationMethodLiteral = 'PEARSON',
) {
  const sortedKey = [...assetIds].sort().join(',');
  return useQuery<CorrelationMatrixResponse>({
    queryKey: ['analytics', 'correlations', sortedKey, from ?? null, to ?? null, method],
    queryFn: () => analyticsApi.fetchCorrelationMatrix({ assetIds, from, to, method }),
    enabled: assetIds.length >= 2,
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  });
}

/**
 * Fetches the Monte Carlo editor defaults (per-class mean/stddev tuples + iteration / horizon /
 * weight defaults). The YAML changes only at deploy time so the result is treated as never stale.
 */
export function useMonteCarloDefaults() {
  return useQuery<MonteCarloDefaultsResponse>({
    queryKey: ['analytics', 'monteCarlo', 'defaults'],
    queryFn: () => analyticsApi.fetchMonteCarloDefaults(),
    staleTime: Infinity,
  });
}

/**
 * Mutation hook for running the Monte Carlo simulation. Consumed via {@code mutation.mutate(req)};
 * the response lives in {@code mutation.data} (or component-local state via {@code onSuccess}).
 */
export function useMonteCarloMutation() {
  return useMutation<MonteCarloResponse, Error, MonteCarloRequest>({
    mutationFn: (request) => analyticsApi.runMonteCarlo(request),
  });
}
