import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MonteCarloProjection } from './MonteCarloProjection';
import { analyticsApi } from '@/api/analytics.api';
import { fireApi } from '@/api/fire.api';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn(), resolvedLanguage: 'en' },
  }),
  I18nextProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

vi.mock('@/api/analytics.api', () => ({
  analyticsApi: {
    fetchMonteCarloDefaults: vi.fn(),
    runMonteCarlo: vi.fn(),
  },
}));

vi.mock('@/api/fire.api', () => ({
  fireApi: { compute: vi.fn() },
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

const DEFAULTS_RESPONSE = {
  defaultIterations: 10000,
  defaultHorizonYears: 20,
  defaultMonthlyContribution: 0,
  defaultCurrentNetWorth: 0,
  defaultTargetNetWorth: null,
  classes: [
    { assetClass: 'STOCK' as const, defaultWeight: 0.5, annualMeanReturn: 0.07, annualStdDev: 0.18 },
    { assetClass: 'BOND' as const, defaultWeight: 0.2, annualMeanReturn: 0.03, annualStdDev: 0.06 },
    { assetClass: 'CASH' as const, defaultWeight: 0.1, annualMeanReturn: 0.01, annualStdDev: 0.01 },
    { assetClass: 'CRYPTO' as const, defaultWeight: 0.1, annualMeanReturn: 0.2, annualStdDev: 0.6 },
    { assetClass: 'GOLD' as const, defaultWeight: 0.1, annualMeanReturn: 0.05, annualStdDev: 0.15 },
    { assetClass: 'FUND' as const, defaultWeight: 0, annualMeanReturn: 0.06, annualStdDev: 0.14 },
    { assetClass: 'CURRENCY' as const, defaultWeight: 0, annualMeanReturn: 0, annualStdDev: 0.08 },
    { assetClass: 'OTHER' as const, defaultWeight: 0, annualMeanReturn: 0.05, annualStdDev: 0.12 },
  ],
};

const SIMULATION_RESPONSE = {
  horizonYears: 20,
  iterations: 10000,
  currentNetWorth: 100000,
  monthlyContribution: 1000,
  targetNetWorth: null,
  fan: Array.from({ length: 20 }, (_, i) => ({
    year: i + 1,
    p10: 100000 + i * 1000,
    p25: 110000 + i * 1500,
    p50: 120000 + i * 2000,
    p75: 130000 + i * 2500,
    p90: 140000 + i * 3000,
  })),
  summary: {
    mean: 158000,
    p10: 119000,
    p50: 158000,
    p90: 197000,
    successProbability: null,
  },
  defaultsApplied: DEFAULTS_RESPONSE.classes.slice(0, 5),
};

const FIRE_RESPONSE = {
  currentNetWorth: 250000,
  avgMonthlyIncome: 30000,
  avgMonthlyExpense: 20000,
  savingsRate: 0.33,
  monthlyContribution: 10000,
  withdrawalRate: 0.04,
  expectedReturn: 0.07,
  targetNumber: 6000000,
  progressRatio: 0.04,
  monthsToFi: 240,
  yearsToFi: 20,
  projectedFiDate: '2046-01-01',
  samplesUsed: 12,
  sufficientData: true,
  trajectory: [],
};

describe('MonteCarloProjection', () => {
  beforeEach(() => {
    vi.mocked(analyticsApi.fetchMonteCarloDefaults).mockReset();
    vi.mocked(analyticsApi.runMonteCarlo).mockReset();
    vi.mocked(fireApi.compute).mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('seeds the editor with the visible-by-default classes from the defaults endpoint', async () => {
    vi.mocked(analyticsApi.fetchMonteCarloDefaults).mockResolvedValue(DEFAULTS_RESPONSE);
    vi.mocked(fireApi.compute).mockResolvedValue(FIRE_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<MonteCarloProjection />, { wrapper: Wrapper });

    await waitFor(() => expect(analyticsApi.fetchMonteCarloDefaults).toHaveBeenCalled());

    // Visible-by-default rows: STOCK, BOND, CASH, CRYPTO, GOLD (defaultWeight > 0).
    // Each row owns a remove button -> exactly 5 such buttons rendered on first mount.
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: /removeRowAria/i })).toHaveLength(5),
    );
    expect(screen.getByText('STOCK')).toBeDefined();
    expect(screen.getByText('BOND')).toBeDefined();
    expect(screen.getByText('CASH')).toBeDefined();
    expect(screen.getByText('CRYPTO')).toBeDefined();
    expect(screen.getByText('GOLD')).toBeDefined();
  });

  it('clicking Run posts the request body and renders the fan + summary cards', async () => {
    vi.mocked(analyticsApi.fetchMonteCarloDefaults).mockResolvedValue(DEFAULTS_RESPONSE);
    vi.mocked(analyticsApi.runMonteCarlo).mockResolvedValue(SIMULATION_RESPONSE);
    vi.mocked(fireApi.compute).mockResolvedValue(FIRE_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<MonteCarloProjection />, { wrapper: Wrapper });

    await waitFor(() => expect(screen.getByText('STOCK')).toBeDefined());

    const runBtn = screen.getByRole('button', { name: /runButton/i });
    await act(async () => fireEvent.click(runBtn));

    await waitFor(() => expect(analyticsApi.runMonteCarlo).toHaveBeenCalled());
    const call = vi.mocked(analyticsApi.runMonteCarlo).mock.calls[0][0];
    expect(call.iterations).toBe(10000);
    expect(call.horizonYears).toBe(20);
    expect(call.allocations.length).toBe(5);
    expect(call.allocations[0].assetClass).toBe('STOCK');

    // Summary card labels rendered.
    await waitFor(() => expect(screen.getByText('analytics.monteCarlo.summaryMedian')).toBeDefined());
    expect(screen.getByText('analytics.monteCarlo.summaryDownside')).toBeDefined();
    expect(screen.getByText('analytics.monteCarlo.summaryUpside')).toBeDefined();
  });

  it('does not auto-submit on slider changes — only on Run click', async () => {
    vi.mocked(analyticsApi.fetchMonteCarloDefaults).mockResolvedValue(DEFAULTS_RESPONSE);
    vi.mocked(fireApi.compute).mockResolvedValue(FIRE_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<MonteCarloProjection />, { wrapper: Wrapper });

    await waitFor(() => expect(screen.getByText('STOCK')).toBeDefined());

    const sliders = screen.getAllByRole('slider');
    await act(async () => {
      fireEvent.change(sliders[0], { target: { value: '5000' } });
      fireEvent.change(sliders[1], { target: { value: '10' } });
    });

    expect(analyticsApi.runMonteCarlo).not.toHaveBeenCalled();
  });

  it('surfaces an error description when the mutation fails', async () => {
    vi.mocked(analyticsApi.fetchMonteCarloDefaults).mockResolvedValue(DEFAULTS_RESPONSE);
    vi.mocked(analyticsApi.runMonteCarlo).mockRejectedValue(new Error('boom'));
    vi.mocked(fireApi.compute).mockResolvedValue(FIRE_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<MonteCarloProjection />, { wrapper: Wrapper });

    await waitFor(() => expect(screen.getByText('STOCK')).toBeDefined());

    const runBtn = screen.getByRole('button', { name: /runButton/i });
    await act(async () => fireEvent.click(runBtn));

    await waitFor(() => expect(analyticsApi.runMonteCarlo).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText('analytics.monteCarlo.errorTitle')).toBeDefined());
  });

  it('removing a row removes it from the editor + the next request body', async () => {
    vi.mocked(analyticsApi.fetchMonteCarloDefaults).mockResolvedValue(DEFAULTS_RESPONSE);
    vi.mocked(analyticsApi.runMonteCarlo).mockResolvedValue(SIMULATION_RESPONSE);
    vi.mocked(fireApi.compute).mockResolvedValue(FIRE_RESPONSE);
    const { Wrapper } = makeWrapper();

    render(<MonteCarloProjection />, { wrapper: Wrapper });

    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: /removeRowAria/i })).toHaveLength(5),
    );

    const removes = screen.getAllByRole('button', { name: /removeRowAria/i });
    await act(async () => fireEvent.click(removes[4])); // remove the 5th row (GOLD)

    expect(screen.getAllByRole('button', { name: /removeRowAria/i }).length).toBe(4);
  });
});
