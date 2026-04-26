import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { BudgetPage } from './BudgetPage';
import { budgetApi } from '@/api/budget.api';
import { tagsApi } from '@/api/tags.api';
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
  budgetApi: {
    listTransactions: vi.fn(),
    summary: vi.fn(),
    listCategories: vi.fn(),
    listSummaries: vi.fn(),
    createTransaction: vi.fn(),
    deleteTransaction: vi.fn(),
    bulkDelete: vi.fn(),
    bulkUpdate: vi.fn(),
    captureSnapshot: vi.fn(),
    createIncomeCategory: vi.fn(),
    createExpenseCategory: vi.fn(),
    updateExpenseCategory: vi.fn(),
  },
}));

vi.mock('@/api/tags.api', () => ({
  tagsApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}));

vi.mock('@/api/budget-rules.api', () => ({
  budgetRulesApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), delete: vi.fn() },
}));

vi.mock('@/api/recurring.api', () => ({
  recurringApi: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    runNow: vi.fn(),
  },
}));

vi.mock('@/api/cashflow.api', () => ({
  cashFlowApi: {
    listBuckets: vi.fn().mockResolvedValue([]),
    replaceBuckets: vi.fn(),
    preview: vi.fn(),
  },
}));

vi.mock('@/api/report.api', () => ({
  reportApi: {
    downloadBudgetCsv: vi.fn(),
    downloadBudgetXlsx: vi.fn(),
  },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <BudgetPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('BudgetPage', () => {
  beforeEach(() => {
    Object.values(budgetApi).forEach((fn) => vi.mocked(fn).mockReset());
    Object.values(tagsApi).forEach((fn) => vi.mocked(fn).mockReset());
    vi.mocked(budgetApi.listTransactions).mockResolvedValue({
      content: [],
      totalElements: 0,
      number: 0,
      size: 20,
    } as never);
    vi.mocked(budgetApi.summary).mockResolvedValue({
      period: '2026-04',
      income: 0,
      expense: 0,
      net: 0,
      savingsRate: 0,
      byCategory: [],
      expenseByCategory: [],
    } as never);
    vi.mocked(budgetApi.listCategories).mockResolvedValue({ income: [], expense: [] } as never);
    vi.mocked(tagsApi.list).mockResolvedValue([] as never);
  });

  it('renders without crashing on empty data', async () => {
    renderPage();

    await waitFor(() => expect(budgetApi.summary).toHaveBeenCalled());
    expect(screen.getAllByText(/budget/i).length).toBeGreaterThan(0);
  });
});
