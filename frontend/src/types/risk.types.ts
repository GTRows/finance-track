/**
 * Portfolio risk/return summary returned by GET /portfolios/{id}/risk.
 * All percentage-style values are decimals (0.12 = 12%).
 */
export interface RiskMetrics {
  snapshotCount: number;
  periodStart: string | null;
  periodEnd: string | null;
  totalReturn: number | null;
  annualVolatility: number | null;
  sharpeRatio: number | null;
  maxDrawdown: number | null;
  bestDay: number | null;
  worstDay: number | null;
  averageDailyReturn: number | null;
  riskFreeRate: number;
  sufficientData: boolean;
}
