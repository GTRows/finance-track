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

export type NotificationSourceType = 'PRICE_ALERT' | 'BUDGET_RULE';

export interface AlertNotification {
  id: string;
  alertId: string | null;
  assetId: string | null;
  sourceType: NotificationSourceType;
  sourceId: string | null;
  message: string;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCount {
  count: number;
}
