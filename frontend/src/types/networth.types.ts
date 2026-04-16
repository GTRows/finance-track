export type NetWorthEventType = 'PURCHASE' | 'INCOME' | 'MILESTONE' | 'NOTE';

export interface NetWorthEvent {
  id: string;
  eventDate: string;
  eventType: NetWorthEventType;
  label: string;
  note: string | null;
  impactTry: number | null;
}

export interface NetWorthPoint {
  date: string;
  totalValueTry: number;
  totalCostTry: number;
}

export interface NetWorthTimeline {
  series: NetWorthPoint[];
  events: NetWorthEvent[];
}

export interface UpsertEventRequest {
  eventDate: string;
  eventType: NetWorthEventType;
  label: string;
  note?: string;
  impactTry?: number;
}
