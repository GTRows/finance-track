import type { AssetType } from './portfolio.types';

export type AlertDirection = 'ABOVE' | 'BELOW';
export type AlertStatus = 'ACTIVE' | 'TRIGGERED' | 'DISABLED';

export interface PriceAlert {
  id: string;
  assetId: string;
  assetSymbol: string;
  assetName: string;
  assetType: AssetType;
  currentPriceTry: number | null;
  direction: AlertDirection;
  thresholdTry: number;
  status: AlertStatus;
  createdAt: string;
  triggeredAt: string | null;
}

export interface CreateAlertRequest {
  assetId: string;
  direction: AlertDirection;
  thresholdTry: number;
}

export interface AlertNotification {
  id: string;
  alertId: string;
  assetId: string;
  message: string;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCount {
  count: number;
}
