/** Asset types tracked by the system. */
export type AssetType = 'CRYPTO' | 'STOCK' | 'GOLD' | 'FUND' | 'CURRENCY' | 'OTHER';

/** Investment transaction types. */
export type InvestmentTxnType = 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW' | 'REBALANCE' | 'BES_CONTRIBUTION';

/** Portfolio category. */
export type PortfolioType =
  | 'INDIVIDUAL'
  | 'BES'
  | 'RETIREMENT'
  | 'EMERGENCY'
  | 'STOCKS'
  | 'CRYPTO'
  | 'REAL_ESTATE'
  | 'OTHER';

/** Portfolio summary returned by the list endpoint. */
export interface Portfolio {
  id: string;
  name: string;
  type: PortfolioType;
  description: string | null;
  createdAt: string;
}

/** Detailed portfolio with holdings (used in the future detail view). */
export interface PortfolioDetail extends Portfolio {
  totalValueTry: number;
  totalCostTry: number;
  pnlTry: number;
  pnlPercent: number;
  holdings: PortfolioHolding[];
}

/** Request body for creating a portfolio. */
export interface CreatePortfolioRequest {
  name: string;
  type: PortfolioType;
  description?: string;
}

/** Request body for updating a portfolio. */
export interface UpdatePortfolioRequest {
  name: string;
  description?: string;
}

/** Single holding within a portfolio. */
export interface PortfolioHolding {
  assetSymbol: string;
  assetName: string;
  assetType: AssetType;
  quantity: number;
  currentPriceTry: number;
  currentValueTry: number;
  avgCostTry: number;
  costBasisTry: number;
  pnlTry: number;
  pnlPercent: number;
  targetWeight: number;
  currentWeight: number;
  weightDeviation: number;
}

/** Live price data from the price sync system. */
export interface PriceData {
  symbol: string;
  priceTry: number;
  priceUsd: number | null;
  change24h: number;
  updatedAt: string;
}

/** Asset master-data row used by the asset picker. */
export interface Asset {
  id: string;
  symbol: string;
  name: string;
  assetType: AssetType;
  currency: string;
  price: number | null;
  priceUsd: number | null;
  priceUpdatedAt: string | null;
}

/** Holding row returned by the holdings list endpoint. */
export interface Holding {
  id: string;
  portfolioId: string;
  assetId: string;
  assetSymbol: string;
  assetName: string;
  assetType: AssetType;
  quantity: number;
  avgCostTry: number | null;
  currentPriceTry: number | null;
  currentValueTry: number | null;
  costBasisTry: number | null;
  pnlTry: number | null;
  pnlPercent: number | null;
  priceUpdatedAt: string | null;
  updatedAt: string;
}

/** Request body for adding a new holding. */
export interface AddHoldingRequest {
  assetId: string;
  quantity: number;
  avgCostTry?: number;
}

/** A single point on the portfolio value history chart. */
export interface PortfolioSnapshot {
  date: string;
  totalValueTry: number;
  totalCostTry: number;
  pnlTry: number;
  pnlPercent: number | null;
}
