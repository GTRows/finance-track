import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { DashboardPage } from './DashboardPage';
import { dashboardApi } from '@/api/dashboard.api';
import { portfolioApi } from '@/api/portfolio.api';
import { budgetApi } from '@/api/budget.api';
import { snapshotApi } from '@/api/snapshot.api';
import { useAuthStore } from '@/store/auth.store';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof I18N>();
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { changeLanguage: vi.fn() },
    }),
  };
});

vi.mock('@/api/dashboard.api', () => ({
  dashboardApi: { get: vi.fn() },
}));
vi.mock('@/api/portfolio.api', () => ({
  portfolioApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
}));
vi.mock('@/api/budget.api', () => ({
  budgetApi: { listSummaries: vi.fn() },
}));
vi.mock('@/api/snapshot.api', () => ({
  snapshotApi: { list: vi.fn() },
}));
vi.mock('@/api/networth.api', () => ({
  netWorthApi: { timeline: vi.fn().mockResolvedValue({ series: [], events: [] }) },
}));
vi.mock('@/api/savings.api', () => ({
  savingsApi: { list: vi.fn().mockResolvedValue([]) },
}));
vi.mock('@/api/debts.api', () => ({
  debtsApi: { list: vi.fn().mockResolvedValue([]) },
}));
vi.mock('@/api/fire.api', () => ({
  fireApi: {
    compute: vi.fn().mockResolvedValue({
      targetNumber: 0,
      withdrawalRate: 0.04,
      progressRatio: 0,
      monthsToFi: null,
      currentNetWorth: 0,
      avgMonthlyIncome: 0,
      avgMonthlyExpense: 0,
      savingsRate: 0,
      monthlyContribution: 0,
      expectedReturn: 0.07,
      yearsToFi: 0,
      projectedFiDate: null,
      samplesUsed: 0,
      sufficientData: false,
      trajectory: [],
    }),
  },
}));

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    onConnect: () => void = () => undefined;
    activate() { /* no-op */ }
    deactivate() { return Promise.resolve(); }
    subscribe() { return { unsubscribe: () => undefined }; }
    get active() { return false; }
  },
}));

function renderDashboard() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.mocked(dashboardApi.get).mockReset();
    vi.mocked(portfolioApi.list).mockReset();
    vi.mocked(budgetApi.listSummaries).mockReset();
    vi.mocked(snapshotApi.list).mockReset();
    useAuthStore.setState({
      user: {
        id: 'u1',
        username: 'ali',
        email: 'a@b',
        role: 'USER',
        emailVerified: true,
        createdAt: '2026-01-01',
      },
      accessToken: 't',
      refreshToken: 'r',
    });
  });

  it('renders without crashing when all data is empty', () => {
    vi.mocked(dashboardApi.get).mockResolvedValue({
      totalNetWorth: 0,
      portfolios: [],
      budget: { period: '2026-04', income: 0, expense: 0, net: 0, savingsRate: 0 },
      upcomingBills: [],
    } as never);
    vi.mocked(portfolioApi.list).mockResolvedValue([] as never);
    vi.mocked(budgetApi.listSummaries).mockResolvedValue([] as never);

    renderDashboard();

    expect(screen.getAllByText(/dashboard/i).length).toBeGreaterThan(0);
  });
});
