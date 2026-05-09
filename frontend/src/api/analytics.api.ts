import client from './client';

export interface CashFlowScheduledItem {
  source: 'recurring' | 'bill';
  label: string;
  kind: 'INCOME' | 'EXPENSE';
  amount: number;
}

export interface CashFlowMonthPoint {
  period: string;
  projectedIncome: number;
  projectedExpense: number;
  net: number;
  endingBalance: number;
  scheduledIncome: number;
  scheduledExpense: number;
  scheduled: CashFlowScheduledItem[];
}

export interface CashFlowProjection {
  avgMonthlyIncome: number;
  avgMonthlyExpense: number;
  avgMonthlyNet: number;
  sampleMonths: number;
  sufficient: boolean;
  startingBalance: number;
  months: CashFlowMonthPoint[];
}

export interface BenchmarkPoint {
  date: string;
  close: number;
}

export interface BenchmarkSeries {
  code: string;
  symbol: string;
  currency: string;
  points: BenchmarkPoint[];
}

export interface BenchmarkResponse {
  days: number;
  series: BenchmarkSeries[];
}

/** A single point on a portfolio comparison series. All monetary fields are TRY-denominated. */
export interface PortfolioComparisonPoint {
  date: string;
  totalValueTry: number;
  totalCostTry: number;
  unrealizedPnlTry: number;
  realizedPnlTry: number;
  totalPnlTry: number;
}

/** One portfolio's chronological series in a multi-portfolio comparison response. */
export interface PortfolioComparisonSeries {
  portfolioId: string;
  name: string;
  points: PortfolioComparisonPoint[];
}

/** Response shape for {@link analyticsApi.fetchPortfolioComparison}. */
export interface PortfolioComparisonResponse {
  currency: string;
  series: PortfolioComparisonSeries[];
}

export interface PortfolioComparisonParams {
  ids: string[];
  from?: string;
  to?: string;
}

/** Sample window metadata returned with a correlation matrix. */
export interface CorrelationSamplePeriod {
  from: string;
  to: string;
  alignedDays: number;
}

/** Method literal mirroring the server-side {@code CorrelationMethod} enum. */
export type CorrelationMethodLiteral = 'PEARSON' | 'SPEARMAN';

/** Response shape for {@link analyticsApi.fetchCorrelationMatrix}. */
export interface CorrelationMatrixResponse {
  assetIds: string[];
  assetSymbols: string[];
  assetNames: string[];
  matrix: Array<Array<number | null>>;
  dataPoints: number[][];
  samplePeriod: CorrelationSamplePeriod;
  method: CorrelationMethodLiteral;
}

export interface CorrelationMatrixParams {
  assetIds: string[];
  from?: string;
  to?: string;
  method?: CorrelationMethodLiteral;
}

/** Macro asset class enum literal mirroring server-side {@code AssetClass}. */
export type AssetClassLiteral =
  | 'STOCK'
  | 'BOND'
  | 'CASH'
  | 'CRYPTO'
  | 'GOLD'
  | 'FUND'
  | 'CURRENCY'
  | 'OTHER';

/** One row of the operator's allocation editor. Mean / stddev nullable -> server falls back to YAML default. */
export interface AllocationClassInput {
  assetClass: AssetClassLiteral;
  weight: number;
  annualMeanReturn?: number | null;
  annualStdDev?: number | null;
}

/** Per-class default tuple as exposed by /defaults plus the resolved tuple echoed in /monte-carlo response. */
export interface AllocationClassDefault {
  assetClass: AssetClassLiteral;
  defaultWeight: number;
  annualMeanReturn: number;
  annualStdDev: number;
}

/** One year boundary on the percentile fan chart. */
export interface YearPercentilePoint {
  year: number;
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
}

/** Headline summary stats. {@code successProbability} is null when the request omits a target. */
export interface MonteCarloSummary {
  mean: number;
  p10: number;
  p50: number;
  p90: number;
  successProbability: number | null;
}

/** Request body for {@link analyticsApi.runMonteCarlo}. */
export interface MonteCarloRequest {
  horizonYears: number;
  iterations: number;
  currentNetWorth: number;
  monthlyContribution: number;
  targetNetWorth?: number | null;
  allocations: AllocationClassInput[];
}

/** Response body for {@link analyticsApi.runMonteCarlo}. */
export interface MonteCarloResponse {
  horizonYears: number;
  iterations: number;
  currentNetWorth: number;
  monthlyContribution: number;
  targetNetWorth: number | null;
  fan: YearPercentilePoint[];
  summary: MonteCarloSummary;
  defaultsApplied: AllocationClassDefault[];
}

/** Response body for {@link analyticsApi.fetchMonteCarloDefaults}. */
export interface MonteCarloDefaultsResponse {
  defaultIterations: number;
  defaultHorizonYears: number;
  defaultMonthlyContribution: number;
  defaultCurrentNetWorth: number;
  defaultTargetNetWorth: number | null;
  classes: AllocationClassDefault[];
}

export const analyticsApi = {
  async projectCashFlow(months?: number, startingBalance?: number): Promise<CashFlowProjection> {
    const params: Record<string, string> = {};
    if (months != null) params.months = String(months);
    if (startingBalance != null) params.startingBalance = String(startingBalance);
    const { data } = await client.get<CashFlowProjection>('/analytics/cash-flow-projection', { params });
    return data;
  },
  async fetchBenchmarks(days = 365): Promise<BenchmarkResponse> {
    const { data } = await client.get<BenchmarkResponse>('/analytics/benchmarks', {
      params: { days: String(days) },
    });
    return data;
  },
  async fetchPortfolioComparison({
    ids,
    from,
    to,
  }: PortfolioComparisonParams): Promise<PortfolioComparisonResponse> {
    const params: Record<string, string> = { ids: ids.join(',') };
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await client.get<PortfolioComparisonResponse>(
      '/analytics/portfolios/compare',
      { params },
    );
    return data;
  },
  async fetchCorrelationMatrix({
    assetIds,
    from,
    to,
    method,
  }: CorrelationMatrixParams): Promise<CorrelationMatrixResponse> {
    const params: Record<string, string> = { assetIds: assetIds.join(',') };
    if (from) params.from = from;
    if (to) params.to = to;
    if (method) params.method = method;
    const { data } = await client.get<CorrelationMatrixResponse>(
      '/analytics/correlations',
      { params },
    );
    return data;
  },
  async fetchMonteCarloDefaults(): Promise<MonteCarloDefaultsResponse> {
    const { data } = await client.get<MonteCarloDefaultsResponse>(
      '/analytics/monte-carlo/defaults',
    );
    return data;
  },
  async runMonteCarlo(request: MonteCarloRequest): Promise<MonteCarloResponse> {
    const { data } = await client.post<MonteCarloResponse>(
      '/analytics/monte-carlo',
      request,
    );
    return data;
  },
};
