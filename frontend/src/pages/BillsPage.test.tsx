import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { BillsPage } from './BillsPage';
import { billsApi } from '@/api/bills.api';
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

vi.mock('@/api/bills.api', () => ({
  billsApi: {
    list: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
    pay: vi.fn(),
    audit: vi.fn(),
    markUsed: vi.fn(),
  },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <BillsPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('BillsPage', () => {
  beforeEach(() => {
    Object.values(billsApi).forEach((fn) => vi.mocked(fn).mockReset());
    vi.mocked(billsApi.list).mockResolvedValue([] as never);
    vi.mocked(billsApi.audit).mockResolvedValue({
      totalMonthlySpend: 0,
      potentialMonthlySavings: 0,
      candidateCount: 0,
      candidates: [],
    } as never);
  });

  it('renders the page header on empty data', async () => {
    renderPage();

    await waitFor(() => expect(billsApi.list).toHaveBeenCalled());
    expect(screen.getAllByText('bills.title').length).toBeGreaterThan(0);
  });
});
