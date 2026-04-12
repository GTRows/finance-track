import client from './client';

export interface DashboardData {
  totalNetWorth: number;
  portfolios: PortfolioSummary[];
  budget: BudgetOverview;
  upcomingBills: UpcomingBill[];
}

export interface PortfolioSummary {
  id: string;
  name: string;
  portfolioType: string;
  valueTry: number;
  costTry: number;
  pnlTry: number;
  pnlPercent: number | null;
}

export interface BudgetOverview {
  period: string;
  income: number;
  expense: number;
  net: number;
  savingsRate: number;
}

export interface UpcomingBill {
  id: string;
  name: string;
  amount: number;
  dueDay: number;
  daysUntilDue: number;
  status: string;
}

export const dashboardApi = {
  get: async (): Promise<DashboardData> => {
    const { data } = await client.get<DashboardData>('/dashboard');
    return data;
  },
};
