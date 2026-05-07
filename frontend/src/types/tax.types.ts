export type TrTaxStatus = 'under' | 'approaching' | 'over' | 'unknown';

export interface TrTaxParameters {
  year: number;
  capitalGainsThresholdTry: number;
  appliesTo: string[];
  dividendStoppageRate: number;
  besDividendStoppageRate: number;
  notes: string | null;
  source: string | null;
}

export interface TrAssetStoppage {
  assetId: string;
  assetSymbol: string | null;
  assetName: string | null;
  grossTry: number;
  withholdingTry: number;
  netTry: number;
}

export interface TrDividendStoppage {
  totalGrossTry: number;
  totalWithholdingTry: number;
  totalNetTry: number;
  byAsset: TrAssetStoppage[];
}

export interface TrCapitalGainsThreshold {
  realizedTry: number;
  thresholdTry: number | null;
  headroomTry: number | null;
  usedRatio: number | null;
  status: TrTaxStatus;
}

export interface TrTaxReport {
  year: number;
  parameters: TrTaxParameters | null;
  dividendStoppage: TrDividendStoppage;
  capitalGains: TrCapitalGainsThreshold;
  warnings: string[];
}
