import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { PortfolioPage } from './PortfolioPage';
import { portfolioApi } from '@/api/portfolio.api';
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
  portfolioApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <PortfolioPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('PortfolioPage', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.list).mockReset();
  });

  it('renders the page header and add button', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue([] as never);
    renderPage();

    await waitFor(() => expect(portfolioApi.list).toHaveBeenCalled());
    expect(screen.getAllByText('portfolio.title').length).toBeGreaterThan(0);
    expect(screen.getAllByRole('button', { name: /portfolio\.addPortfolio/ }).length).toBeGreaterThan(0);
  });

  it('shows the empty-state hint when no portfolios exist', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue([] as never);
    renderPage();

    await waitFor(() =>
      expect(screen.getAllByText('portfolio.noHoldings').length).toBeGreaterThan(0),
    );
  });

  it('renders the portfolio list when data is present', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue([
      {
        id: 'p1',
        name: 'Main',
        type: 'INDIVIDUAL',
        active: true,
        description: null,
        createdAt: '2026-01-01',
      },
    ] as never);
    renderPage();

    await waitFor(() => expect(screen.getAllByText('Main').length).toBeGreaterThan(0));
  });
});
