export interface Dividend {
  id: string;
  portfolioId: string;
  assetId: string;
  assetSymbol: string | null;
  assetName: string | null;
  amountPerShare: number | null;
  shares: number | null;
  grossAmount: number;
  withholdingTax: number;
  netAmount: number;
  currency: string;
  netAmountTry: number;
  paymentDate: string;
  exDividendDate: string | null;
  notes: string | null;
}

export interface RecordDividendRequest {
  assetId: string;
  grossAmount: number;
  withholdingTax?: number;
  currency?: string;
  amountPerShare?: number;
  shares?: number;
  paymentDate: string;
  exDividendDate?: string;
  notes?: string;
}
