import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { AnalyticsPage } from './AnalyticsPage';
import { budgetApi } from '@/api/budget.api';
import { portfolioApi } from '@/api/portfolio.api';
import { snapshotApi } from '@/api/snapshot.api';
import { analyticsApi } from '@/api/analytics.api';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof I18N>();
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { changeLanguage: vi.fn(), resolvedLanguage: 'en' },
    }),
  };
});

vi.mock('@/api/budget.api', () => ({
  budgetApi: { listSummaries: vi.fn() },
}));
vi.mock('@/api/portfolio.api', () => ({
  portfolioApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
}));
vi.mock('@/api/snapshot.api', () => ({
  snapshotApi: { list: vi.fn() },
}));
vi.mock('@/api/analytics.api', () => ({
  analyticsApi: { projectCashFlow: vi.fn(), fetchBenchmarks: vi.fn() },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <AnalyticsPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('AnalyticsPage', () => {
  beforeEach(() => {
    vi.mocked(budgetApi.listSummaries).mockResolvedValue([] as never);
    vi.mocked(portfolioApi.list).mockResolvedValue([] as never);
    vi.mocked(snapshotApi.list).mockResolvedValue([] as never);
    vi.mocked(analyticsApi.projectCashFlow).mockResolvedValue({
      avgMonthlyIncome: 0,
      avgMonthlyExpense: 0,
      avgMonthlyNet: 0,
      sampleMonths: 0,
      sufficient: false,
      startingBalance: 0,
      months: [],
    } as never);
    vi.mocked(analyticsApi.fetchBenchmarks).mockResolvedValue({ days: 365, series: [] } as never);
  });

  it('renders without crashing on empty data', async () => {
    renderPage();

    await waitFor(() => expect(portfolioApi.list).toHaveBeenCalled());
    const titles = await screen.findAllByText('analytics.title');
    expect(titles.length).toBeGreaterThan(0);
  });
});
