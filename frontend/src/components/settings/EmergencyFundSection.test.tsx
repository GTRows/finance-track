import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import type * as I18N from 'react-i18next';
import { EmergencyFundSection } from './EmergencyFundSection';
import { dashboardApi } from '@/api/dashboard.api';
import { createWrapper } from '@/test-utils/queryWrapper';
import type { EmergencyFundResponse } from '@/types/emergency-fund.types';

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

vi.mock('@/api/dashboard.api', () => ({
  dashboardApi: {
    emergencyFund: vi.fn(),
    updateEmergencyFundTypes: vi.fn(),
    updateEmergencyFundConfig: vi.fn(),
  },
}));

function makeResponse(overrides: Partial<EmergencyFundResponse> = {}): EmergencyFundResponse {
  return {
    currentReserve: '6000',
    buckets: [{ currency: 'TRY', totalBalance: '6000' }],
    monthlyAverageExpense: '1000',
    monthsCovered: '6.0',
    status: 'amber',
    includedTypes: ['BANK_SAVINGS'],
    sampleMonths: 12,
    targetMonths: 6,
    amberFloorMonths: 3,
    ...overrides,
  };
}

function renderSection() {
  const { Wrapper } = createWrapper();
  return render(<EmergencyFundSection />, { wrapper: Wrapper });
}

describe('EmergencyFundSection', () => {
  beforeEach(() => {
    vi.mocked(dashboardApi.emergencyFund).mockReset();
    vi.mocked(dashboardApi.updateEmergencyFundConfig).mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders current target and amber-floor values', async () => {
    vi.mocked(dashboardApi.emergencyFund).mockResolvedValue(
      makeResponse({ targetMonths: 6, amberFloorMonths: 3 }),
    );

    renderSection();

    const target = await screen.findByLabelText('emergencyFund.targetMonths');
    const amber = screen.getByLabelText('emergencyFund.amberFloorMonths');
    expect((target as HTMLInputElement).value).toBe('6');
    expect((amber as HTMLInputElement).value).toBe('3');
  });

  it('clamps amber-floor when target decreases below current amber-floor', async () => {
    vi.mocked(dashboardApi.emergencyFund).mockResolvedValue(
      makeResponse({ targetMonths: 9, amberFloorMonths: 8 }),
    );
    vi.mocked(dashboardApi.updateEmergencyFundConfig).mockResolvedValue(
      makeResponse({ targetMonths: 4, amberFloorMonths: 3 }),
    );

    renderSection();

    const target = await screen.findByLabelText('emergencyFund.targetMonths');
    fireEvent.change(target, { target: { value: '4' } });

    await waitFor(() => {
      expect(dashboardApi.updateEmergencyFundConfig).toHaveBeenCalledWith({
        types: ['BANK_SAVINGS'],
        targetMonths: 4,
        amberFloorMonths: 3,
      });
    });
  });

  it('locks the BANK_SAVINGS chip', async () => {
    vi.mocked(dashboardApi.emergencyFund).mockResolvedValue(
      makeResponse({ includedTypes: ['BANK_SAVINGS'] }),
    );

    renderSection();

    await screen.findByLabelText('emergencyFund.targetMonths');
    const savingsButtons = screen.getAllByRole('button', {
      name: /emergencyFund\.toggleSavings/,
    });
    expect(savingsButtons[0].hasAttribute('disabled')).toBe(true);
  });
});
