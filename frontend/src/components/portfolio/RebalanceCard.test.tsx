import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import type * as I18N from 'react-i18next';
import { RebalanceCard } from './RebalanceCard';
import { rebalanceApi } from '@/api/rebalance.api';
import { allocationApi } from '@/api/allocation.api';
import { holdingApi } from '@/api/holding.api';
import { settingsApi } from '@/api/settings.api';
import { accountsApi } from '@/api/accounts.api';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof I18N>();
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, opts?: Record<string, unknown>) => {
        if (opts && typeof opts === 'object') {
          if ('count' in opts) return `${key}:${String(opts.count)}`;
        }
        return key;
      },
      i18n: { changeLanguage: vi.fn(), resolvedLanguage: 'en' },
    }),
  };
});

vi.mock('@/api/rebalance.api', () => ({
  rebalanceApi: { preview: vi.fn(), commit: vi.fn(), updateThreshold: vi.fn() },
}));
vi.mock('@/api/allocation.api', () => ({
  allocationApi: { get: vi.fn(), set: vi.fn() },
}));
vi.mock('@/api/holding.api', () => ({
  holdingApi: {
    list: vi.fn(),
    add: vi.fn(),
    delete: vi.fn(),
    togglePin: vi.fn(),
  },
}));
vi.mock('@/api/settings.api', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn(), completeOnboarding: vi.fn() },
}));
vi.mock('@/api/accounts.api', () => ({
  accountsApi: {
    list: vi.fn(),
    get: vi.fn(),
    totals: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

function renderCard() {
  const { Wrapper } = createWrapper();
  return render(<RebalanceCard portfolioId="p1" />, { wrapper: Wrapper });
}

const baseAllocation = {
  totalValueTry: 1000,
  configured: true,
  rows: [],
};

const baseHoldings = [
  {
    id: 'h1',
    portfolioId: 'p1',
    assetId: 'a1',
    assetSymbol: 'AAA',
    assetName: 'AAA',
    assetType: 'STOCK' as const,
    quantity: 10,
    avgCostTry: 100,
    currentPriceTry: 100,
    currentValueTry: 1000,
    costBasisTry: 1000,
    pnlTry: 0,
    pnlPercent: 0,
    pinned: false,
    priceUpdatedAt: null,
    updatedAt: '2026-01-01',
  },
];

const baseSettings = {
  currency: 'TRY',
  language: 'tr',
  theme: 'dark',
  timezone: 'Europe/Istanbul',
  onboardingCompleted: true,
};

const baseAccounts = [
  {
    id: 'acc1',
    name: 'Checking',
    accountType: 'BANK_CHECKING',
    currency: 'TRY',
    institution: null,
    accountNumberSuffix: null,
    notes: null,
    currentBalance: 5000,
    archived: false,
    createdAt: '2026-01-01',
  },
];

describe('RebalanceCard', () => {
  beforeEach(() => {
    vi.mocked(rebalanceApi.preview).mockReset();
    vi.mocked(rebalanceApi.commit).mockReset();
    vi.mocked(rebalanceApi.updateThreshold).mockReset();
    vi.mocked(allocationApi.get).mockResolvedValue(baseAllocation as never);
    vi.mocked(holdingApi.list).mockResolvedValue(baseHoldings as never);
    vi.mocked(settingsApi.get).mockResolvedValue(baseSettings as never);
    vi.mocked(accountsApi.list).mockResolvedValue(baseAccounts as never);
  });

  afterEach(() => {
    cleanup();
  });

  it('renders nothing when allocation is not configured', async () => {
    vi.mocked(allocationApi.get).mockResolvedValue({
      ...baseAllocation,
      configured: false,
    } as never);

    const { container } = renderCard();

    await waitFor(() => {
      expect(container.firstChild).toBeNull();
    });
  });

  it('disables the generate button until an account is picked', async () => {
    renderCard();

    const button = await screen.findByRole('button', { name: 'rebalance.generateButton' });
    expect((button as HTMLButtonElement).disabled).toBe(true);
  });

  it('renders preview rows after a successful preview', async () => {
    vi.mocked(rebalanceApi.preview).mockResolvedValueOnce({
      proposalId: 'prop1',
      totalValueTry: 1000,
      accountCashTry: 5000,
      driftThresholdPercent: 1,
      suggestions: [
        {
          index: 0,
          assetId: 'a1',
          symbol: 'AAA',
          assetType: 'STOCK',
          action: 'SELL',
          quantity: 1,
          estimatedPriceTry: 100,
          estimatedAmountTry: 100,
          currentValueTry: 1000,
          currentWeightPercent: 60,
          targetWeightPercent: 50,
          driftPercentBefore: 10,
          warning: null,
        },
      ],
      projectedDriftAfterPercent: 0,
      summaryWarnings: [],
      expiresAt: new Date().toISOString(),
    } as never);

    renderCard();

    await screen.findByText('rebalance.header');
    await waitFor(() => {
      expect(screen.getByText('Checking (TRY)')).toBeTruthy();
    });
    const accountSelect = screen.getByRole('combobox') as HTMLSelectElement;
    fireEvent.change(accountSelect, { target: { value: 'acc1' } });

    await waitFor(() => {
      const button = screen.getByRole('button', {
        name: 'rebalance.generateButton',
      }) as HTMLButtonElement;
      expect(button.disabled).toBe(false);
    });
    const button = screen.getByRole('button', { name: 'rebalance.generateButton' });
    fireEvent.click(button);

    await waitFor(() => {
      expect(rebalanceApi.preview).toHaveBeenCalled();
    });
    await screen.findByText('AAA');
  });

  it('commits only the ticked indices', async () => {
    vi.mocked(rebalanceApi.preview).mockResolvedValueOnce({
      proposalId: 'prop1',
      totalValueTry: 1000,
      accountCashTry: 5000,
      driftThresholdPercent: 1,
      suggestions: [
        {
          index: 0,
          assetId: 'a1',
          symbol: 'AAA',
          assetType: 'STOCK',
          action: 'SELL',
          quantity: 1,
          estimatedPriceTry: 100,
          estimatedAmountTry: 100,
          currentValueTry: 1000,
          currentWeightPercent: 60,
          targetWeightPercent: 50,
          driftPercentBefore: 10,
          warning: null,
        },
        {
          index: 1,
          assetId: 'a2',
          symbol: 'BBB',
          assetType: 'STOCK',
          action: 'BUY',
          quantity: 2,
          estimatedPriceTry: 50,
          estimatedAmountTry: 100,
          currentValueTry: 500,
          currentWeightPercent: 30,
          targetWeightPercent: 40,
          driftPercentBefore: -10,
          warning: null,
        },
      ],
      projectedDriftAfterPercent: 0,
      summaryWarnings: [],
      expiresAt: new Date().toISOString(),
    } as never);
    vi.mocked(rebalanceApi.commit).mockResolvedValueOnce({
      proposalId: 'prop1',
      committedCount: 1,
      transactionIds: ['t1'],
    } as never);

    renderCard();

    // Wait for accounts to populate the picker
    await waitFor(() => {
      expect(screen.getByText('Checking (TRY)')).toBeTruthy();
    });
    const accountSelect = screen.getByRole('combobox') as HTMLSelectElement;
    fireEvent.change(accountSelect, { target: { value: 'acc1' } });

    await waitFor(() => {
      const button = screen.getByRole('button', {
        name: 'rebalance.generateButton',
      }) as HTMLButtonElement;
      expect(button.disabled).toBe(false);
    });

    const generate = screen.getByRole('button', {
      name: 'rebalance.generateButton',
    }) as HTMLButtonElement;
    fireEvent.click(generate);

    await waitFor(() => {
      expect(screen.getByText('AAA')).toBeTruthy();
    });

    // Untick the second row (index 1)
    const checkbox1 = screen.getByLabelText('select-1') as HTMLInputElement;
    fireEvent.click(checkbox1);

    const commit = screen.getByRole('button', { name: /rebalance.commitButton/ });
    fireEvent.click(commit);

    await waitFor(() => {
      expect(rebalanceApi.commit).toHaveBeenCalledWith('p1', {
        proposalId: 'prop1',
        accountId: 'acc1',
        selectedIndices: [0],
      });
    });
  });
});
