import client from './client';

export interface CapitalGainsYearSummary {
  year: number;
  proceeds: number;
  costBasis: number;
  fees: number;
  realizedGain: number;
  dividendsNetTry: number;
  eventCount: number;
}

export interface CapitalGainsEvent {
  transactionId: string;
  portfolioId: string;
  portfolioName: string | null;
  assetId: string;
  assetSymbol: string | null;
  assetName: string | null;
  txnDate: string;
  quantity: number;
  pricePerUnit: number;
  proceeds: number;
  costBasis: number;
  fee: number;
  realizedGain: number;
}

export interface CapitalGainsReport {
  year: number | null;
  totalProceeds: number;
  totalCostBasis: number;
  totalFees: number;
  realizedGain: number;
  dividendsNetTry: number;
  byYear: CapitalGainsYearSummary[];
  events: CapitalGainsEvent[];
}

export const capitalGainsApi = {
  async fetch(year?: number | null): Promise<CapitalGainsReport> {
    const params: Record<string, string> = {};
    if (year != null) params.year = String(year);
    const { data } = await client.get<CapitalGainsReport>('/reports/capital-gains', { params });
    return data;
  },
};
