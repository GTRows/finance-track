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

export const analyticsApi = {
  async projectCashFlow(months?: number, startingBalance?: number): Promise<CashFlowProjection> {
    const params: Record<string, string> = {};
    if (months != null) params.months = String(months);
    if (startingBalance != null) params.startingBalance = String(startingBalance);
    const { data } = await client.get<CashFlowProjection>('/analytics/cash-flow-projection', { params });
    return data;
  },
};
