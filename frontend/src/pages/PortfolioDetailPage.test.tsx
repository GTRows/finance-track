import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { PortfolioDetailPage } from './PortfolioDetailPage';
import { portfolioApi } from '@/api/portfolio.api';
import { holdingApi } from '@/api/holding.api';
import { snapshotApi } from '@/api/snapshot.api';
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

vi.mock('@/api/portfolio.api', () => ({
  portfolioApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
}));
vi.mock('@/api/holding.api', () => ({
  holdingApi: { list: vi.fn(), add: vi.fn(), delete: vi.fn(), togglePin: vi.fn() },
}));
vi.mock('@/api/snapshot.api', () => ({
  snapshotApi: { list: vi.fn() },
}));
vi.mock('@/api/transaction.api', () => ({
  transactionApi: { list: vi.fn().mockResolvedValue([]), record: vi.fn(), delete: vi.fn() },
}));
vi.mock('@/api/dividend.api', () => ({
  dividendApi: {
    listForPortfolio: vi.fn().mockResolvedValue([]),
    record: vi.fn(),
    remove: vi.fn(),
  },
}));
vi.mock('@/api/allocation.api', () => ({
  allocationApi: {
    get: vi.fn().mockResolvedValue({ configured: false, targets: [], totalValueTry: 0 }),
    set: vi.fn(),
  },
}));
vi.mock('@/api/risk.api', () => ({
  riskApi: { get: vi.fn().mockResolvedValue({ sufficientData: false }) },
}));
vi.mock('@/api/asset.api', () => ({
  assetApi: { list: vi.fn().mockResolvedValue([]), get: vi.fn(), history: vi.fn() },
}));
vi.mock('@/api/price.api', () => ({
  priceApi: { refresh: vi.fn(), refreshAsset: vi.fn() },
}));
vi.mock('@/api/report.api', () => ({
  reportApi: {
    downloadPortfolioPdf: vi.fn(),
    downloadBudgetCsv: vi.fn(),
    downloadBudgetXlsx: vi.fn(),
  },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter initialEntries={['/portfolios/p1']}>
      <Routes>
        <Route path="/portfolios/:id" element={<PortfolioDetailPage />} />
      </Routes>
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('PortfolioDetailPage', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.get).mockReset();
    vi.mocked(holdingApi.list).mockReset();
    vi.mocked(snapshotApi.list).mockReset();
    vi.mocked(holdingApi.list).mockResolvedValue([] as never);
    vi.mocked(snapshotApi.list).mockResolvedValue([] as never);
  });

  it('renders the portfolio name when loaded', async () => {
    vi.mocked(portfolioApi.get).mockResolvedValue({
      id: 'p1',
      name: 'Main',
      type: 'INDIVIDUAL',
      active: true,
      description: null,
      createdAt: '2026-01-01',
    } as never);

    renderPage();

    expect(await screen.findByText('Main')).toBeDefined();
  });
});
