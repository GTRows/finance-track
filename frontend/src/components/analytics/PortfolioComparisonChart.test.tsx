import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PortfolioComparisonChart } from './PortfolioComparisonChart';
import { portfolioApi } from '@/api/portfolio.api';
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
vi.mock('@/api/analytics.api', () => ({
  analyticsApi: { fetchPortfolioComparison: vi.fn() },
}));

vi.mock('recharts', async () => {
  const actual = await vi.importActual('recharts');
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="recharts-container" style={{ width: 800, height: 300 }}>
        {children}
      </div>
    ),
  };
});

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
  { id: 'p2', name: 'BES', type: 'BES', description: null, createdAt: '' },
] as never;

const COMPARE_RESPONSE = {
  currency: 'TRY',
  series: [
    {
      portfolioId: 'p1',
      name: 'Main',
      points: [
        {
          date: '2026-01-01',
          totalValueTry: 100,
          totalCostTry: 80,
          unrealizedPnlTry: 20,
          realizedPnlTry: 0,
          totalPnlTry: 20,
        },
        {
          date: '2026-02-01',
          totalValueTry: 120,
          totalCostTry: 80,
          unrealizedPnlTry: 40,
          realizedPnlTry: 0,
          totalPnlTry: 40,
        },
      ],
    },
    {
      portfolioId: 'p2',
      name: 'BES',
      points: [
        {
          date: '2026-01-01',
          totalValueTry: 200,
          totalCostTry: 150,
          unrealizedPnlTry: 50,
          realizedPnlTry: 0,
          totalPnlTry: 50,
        },
        {
          date: '2026-02-01',
          totalValueTry: 220,
          totalCostTry: 150,
          unrealizedPnlTry: 70,
          realizedPnlTry: 0,
          totalPnlTry: 70,
        },
      ],
    },
  ],
} as never;

describe('PortfolioComparisonChart', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.list).mockReset();
    vi.mocked(analyticsApi.fetchPortfolioComparison).mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('shows the empty-selection card when no portfolios exist', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue([] as never);
    const { Wrapper } = makeWrapper();

    render(<PortfolioComparisonChart />, { wrapper: Wrapper });

    await waitFor(() => {
      expect(screen.getByText('analytics.compare.emptyDesc')).toBeDefined();
    });
    expect(analyticsApi.fetchPortfolioComparison).not.toHaveBeenCalled();
  });

  it('seeds first two portfolios into the selection and fetches the comparison series', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue(PORTFOLIOS);
    vi.mocked(analyticsApi.fetchPortfolioComparison).mockResolvedValue(COMPARE_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<PortfolioComparisonChart />, { wrapper: Wrapper });

    await waitFor(() =>
      expect(analyticsApi.fetchPortfolioComparison).toHaveBeenCalledTimes(1),
    );
    const callArgs = vi.mocked(analyticsApi.fetchPortfolioComparison).mock.calls[0][0];
    expect(callArgs.ids).toEqual(['p1', 'p2']);
  });

  it('mode toggle changes mode without re-fetching the comparison data', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue(PORTFOLIOS);
    vi.mocked(analyticsApi.fetchPortfolioComparison).mockResolvedValue(COMPARE_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<PortfolioComparisonChart />, { wrapper: Wrapper });

    await waitFor(() =>
      expect(analyticsApi.fetchPortfolioComparison).toHaveBeenCalledTimes(1),
    );

    const absoluteBtn = screen.getByRole('button', {
      name: 'analytics.compare.modeAbsolute',
    });
    expect(absoluteBtn).toBeDefined();

    await act(async () => {
      fireEvent.click(absoluteBtn);
    });

    // Mode is a client-side rendering toggle and should NOT trigger a new API call.
    expect(analyticsApi.fetchPortfolioComparison).toHaveBeenCalledTimes(1);
  });

  it('range preset click triggers a refetch with a different from value', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValue(PORTFOLIOS);
    vi.mocked(analyticsApi.fetchPortfolioComparison).mockResolvedValue(COMPARE_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<PortfolioComparisonChart />, { wrapper: Wrapper });

    await waitFor(() =>
      expect(analyticsApi.fetchPortfolioComparison).toHaveBeenCalledTimes(1),
    );
    const initialFrom = vi.mocked(analyticsApi.fetchPortfolioComparison).mock.calls[0][0].from;

    // Default preset is 1Y; clicking the ALL preset clears `from` -> different param.
    const allBtn = screen.getByRole('button', { name: 'analytics.compare.rangeALL' });
    await act(async () => {
      fireEvent.click(allBtn);
    });

    await waitFor(() => {
      const calls = vi.mocked(analyticsApi.fetchPortfolioComparison).mock.calls;
      expect(calls.length).toBeGreaterThanOrEqual(2);
    });
    const lastCall = vi.mocked(analyticsApi.fetchPortfolioComparison).mock.calls.at(-1);
    expect(lastCall?.[0].from).not.toBe(initialFrom);
  });
});
