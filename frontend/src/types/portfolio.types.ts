/** Asset types tracked by the system. */
export type AssetType = 'CRYPTO' | 'STOCK' | 'GOLD' | 'FUND' | 'CURRENCY' | 'OTHER';

/** Investment transaction types. */
export type InvestmentTxnType = 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW' | 'REBALANCE' | 'BES_CONTRIBUTION';

/** Portfolio summary returned in list views. */
export interface Portfolio {
  id: string;
  name: string;
  type: 'BIREYSEL' | 'BES';
  totalValueTry: number;
  totalCostTry: number;
  pnlTry: number;
  pnlPercent: number;
  holdings: PortfolioHolding[];
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
