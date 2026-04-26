import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { CapitalGainsPage } from './CapitalGainsPage';
import { capitalGainsApi } from '@/api/capitalgains.api';
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

vi.mock('@/api/capitalgains.api', () => ({
  capitalGainsApi: { fetch: vi.fn() },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <CapitalGainsPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('CapitalGainsPage', () => {
  beforeEach(() => {
    vi.mocked(capitalGainsApi.fetch).mockReset();
    vi.mocked(capitalGainsApi.fetch).mockResolvedValue({
      year: null,
      totalProceeds: 0,
      totalCostBasis: 0,
      totalFees: 0,
      realizedGain: 0,
      dividendsNetTry: 0,
      byYear: [],
      events: [],
    } as never);
  });

  it('renders the page header on empty data', async () => {
    renderPage();
    const titles = await screen.findAllByText('capitalGains.title');
    expect(titles.length).toBeGreaterThan(0);
  });
});
