import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AssetCorrelationMatrix } from './AssetCorrelationMatrix';
import { portfolioApi } from '@/api/portfolio.api';
import { holdingApi } from '@/api/holding.api';
import { analyticsApi } from '@/api/analytics.api';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn(), resolvedLanguage: 'en' },
  }),
  I18nextProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

vi.mock('@/api/portfolio.api', () => ({
  portfolioApi: { list: vi.fn() },
}));
vi.mock('@/api/holding.api', () => ({
  holdingApi: { list: vi.fn() },
}));
vi.mock('@/api/analytics.api', () => ({
  analyticsApi: { fetchCorrelationMatrix: vi.fn() },
}));

interface WrapperResult {
  Wrapper: React.FC<{ children: React.ReactNode }>;
}

function makeWrapper(): WrapperResult {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Infinity, staleTime: 0 },
      mutations: { retry: false },
    },
  });
  const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { Wrapper };
}

const PORTFOLIOS = [
  { id: 'p1', name: 'Main', type: 'INDIVIDUAL', description: null, createdAt: '' },
] as never;

const HOLDINGS = [
  {
    id: 'h1',
    portfolioId: 'p1',
    assetId: 'a1',
    assetSymbol: 'BTC',
    assetName: 'Bitcoin',
    assetType: 'CRYPTO',
    quantity: 1,
    avgCostTry: null,
    currentPriceTry: null,
    currentValueTry: null,
    costBasisTry: null,
    pnlTry: null,
    pnlPercent: null,
    pinned: false,
    priceUpdatedAt: null,
    updatedAt: '',
  },
  {
    id: 'h2',
    portfolioId: 'p1',
    assetId: 'a2',
    assetSymbol: 'ETH',
    assetName: 'Ethereum',
    assetType: 'CRYPTO',
    quantity: 1,
    avgCostTry: null,
    currentPriceTry: null,
    currentValueTry: null,
    costBasisTry: null,
    pnlTry: null,
    pnlPercent: null,
    pinned: false,
    priceUpdatedAt: null,
    updatedAt: '',
  },
  {
    id: 'h3',
    portfolioId: 'p1',
    assetId: 'a3',
    assetSymbol: 'GOLD',
    assetName: 'Gold',
    assetType: 'GOLD',
    quantity: 1,
    avgCostTry: null,
    currentPriceTry: null,
    currentValueTry: null,
    costBasisTry: null,
    pnlTry: null,
    pnlPercent: null,
    pinned: false,
    priceUpdatedAt: null,
    updatedAt: '',
  },
] as never;

const MATRIX_RESPONSE = {
  assetIds: ['a1', 'a2', 'a3'],
  assetSymbols: ['BTC', 'ETH', 'GOLD'],
  assetNames: ['Bitcoin', 'Ethereum', 'Gold'],
  matrix: [
    [1.0, 0.6, null],
    [0.6, 1.0, 0.2],
    [null, 0.2, 1.0],
  ],
  dataPoints: [
    [30, 28, 0],
    [28, 30, 25],
    [0, 25, 30],
  ],
  samplePeriod: { from: '2026-02-01', to: '2026-05-01', alignedDays: 25 },
  method: 'PEARSON',
} as never;

describe('AssetCorrelationMatrix', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.list).mockReset();
    vi.mocked(holdingApi.list).mockReset();
    vi.mocked(analyticsApi.fetchCorrelationMatrix).mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the empty state when fewer than 2 assets are selected', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue(PORTFOLIOS);
    vi.mocked(holdingApi.list).mockResolvedValue(HOLDINGS);
    const { Wrapper } = makeWrapper();

    render(<AssetCorrelationMatrix />, { wrapper: Wrapper });

    await waitFor(() => {
      expect(screen.getByText('analytics.correlations.emptyDesc')).toBeDefined();
    });
    expect(analyticsApi.fetchCorrelationMatrix).not.toHaveBeenCalled();
  });

  it('renders an N x N grid of cells when N assets are selected and the data resolves', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue(PORTFOLIOS);
    vi.mocked(holdingApi.list).mockResolvedValue(HOLDINGS);
    vi.mocked(analyticsApi.fetchCorrelationMatrix).mockResolvedValue(MATRIX_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<AssetCorrelationMatrix />, { wrapper: Wrapper });

    await waitFor(() => expect(holdingApi.list).toHaveBeenCalled());

    const trigger = screen.getByRole('button', {
      name: /selectAssetsPlaceholder/i,
    });
    await act(async () => {
      fireEvent.click(trigger);
    });

    const checkboxes = screen.getAllByRole('checkbox');
    await act(async () => {
      fireEvent.click(checkboxes[0]);
      fireEvent.click(checkboxes[1]);
      fireEvent.click(checkboxes[2]);
    });

    await waitFor(() =>
      expect(analyticsApi.fetchCorrelationMatrix).toHaveBeenCalled(),
    );

    const cells = screen.getAllByRole('gridcell');
    // 3 x 3 = 9 cells (diagonal + off-diagonal + null cell still counted).
    expect(cells.length).toBe(9);
  });

  it('renders the diagonal cells with an em dash, not a numeric value', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue(PORTFOLIOS);
    vi.mocked(holdingApi.list).mockResolvedValue(HOLDINGS);
    vi.mocked(analyticsApi.fetchCorrelationMatrix).mockResolvedValue(MATRIX_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<AssetCorrelationMatrix />, { wrapper: Wrapper });

    await waitFor(() => expect(holdingApi.list).toHaveBeenCalled());

    const trigger = screen.getByRole('button', {
      name: /selectAssetsPlaceholder/i,
    });
    await act(async () => fireEvent.click(trigger));
    const checkboxes = screen.getAllByRole('checkbox');
    await act(async () => {
      fireEvent.click(checkboxes[0]);
      fireEvent.click(checkboxes[1]);
      fireEvent.click(checkboxes[2]);
    });

    await waitFor(() =>
      expect(analyticsApi.fetchCorrelationMatrix).toHaveBeenCalled(),
    );

    const dashes = screen.getAllByText('—');
    // Diagonal of a 3x3 matrix = 3 dash cells.
    expect(dashes.length).toBe(3);
  });

  it('renders null cells with the n/a label', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue(PORTFOLIOS);
    vi.mocked(holdingApi.list).mockResolvedValue(HOLDINGS);
    vi.mocked(analyticsApi.fetchCorrelationMatrix).mockResolvedValue(MATRIX_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<AssetCorrelationMatrix />, { wrapper: Wrapper });

    await waitFor(() => expect(holdingApi.list).toHaveBeenCalled());

    const trigger = screen.getByRole('button', {
      name: /selectAssetsPlaceholder/i,
    });
    await act(async () => fireEvent.click(trigger));
    const checkboxes = screen.getAllByRole('checkbox');
    await act(async () => {
      fireEvent.click(checkboxes[0]);
      fireEvent.click(checkboxes[1]);
      fireEvent.click(checkboxes[2]);
    });

    await waitFor(() =>
      expect(analyticsApi.fetchCorrelationMatrix).toHaveBeenCalled(),
    );

    // Two null cells in MATRIX_RESPONSE: (0,2) and (2,0) -> 2 'naLabel' cells.
    const naCells = screen.getAllByText('analytics.correlations.naLabel');
    expect(naCells.length).toBe(2);
  });

  it('flipping the method toggle Pearson -> Spearman triggers a refetch with method=SPEARMAN', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue(PORTFOLIOS);
    vi.mocked(holdingApi.list).mockResolvedValue(HOLDINGS);
    vi.mocked(analyticsApi.fetchCorrelationMatrix).mockResolvedValue(MATRIX_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<AssetCorrelationMatrix />, { wrapper: Wrapper });

    await waitFor(() => expect(holdingApi.list).toHaveBeenCalled());

    const trigger = screen.getByRole('button', {
      name: /selectAssetsPlaceholder/i,
    });
    await act(async () => fireEvent.click(trigger));
    const checkboxes = screen.getAllByRole('checkbox');
    await act(async () => {
      fireEvent.click(checkboxes[0]);
      fireEvent.click(checkboxes[1]);
    });

    await waitFor(() =>
      expect(analyticsApi.fetchCorrelationMatrix).toHaveBeenCalled(),
    );
    const firstCall = vi.mocked(analyticsApi.fetchCorrelationMatrix).mock.calls[0][0];
    expect(firstCall.method).toBe('PEARSON');

    const spearmanBtn = screen.getByRole('button', {
      name: 'analytics.correlations.methodSpearman',
    });
    await act(async () => {
      fireEvent.click(spearmanBtn);
    });

    await waitFor(() => {
      const calls = vi.mocked(analyticsApi.fetchCorrelationMatrix).mock.calls;
      expect(calls.length).toBeGreaterThanOrEqual(2);
    });
    const lastCall = vi.mocked(analyticsApi.fetchCorrelationMatrix).mock.calls.at(-1);
    expect(lastCall?.[0].method).toBe('SPEARMAN');
  });
});
