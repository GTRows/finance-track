import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { dashboardApi } from '@/api/dashboard.api';
import { useEmergencyFund, useUpdateEmergencyFundTypes } from './useEmergencyFund';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/dashboard.api', () => ({
  dashboardApi: {
    emergencyFund: vi.fn(),
    updateEmergencyFundTypes: vi.fn(),
  },
}));

describe('useEmergencyFund', () => {
  beforeEach(() => {
    vi.mocked(dashboardApi.emergencyFund).mockReset();
    vi.mocked(dashboardApi.updateEmergencyFundTypes).mockReset();
  });

  it('queries the correct URL with default queryKey', async () => {
    vi.mocked(dashboardApi.emergencyFund).mockResolvedValueOnce({
      currentReserve: '5000',
      buckets: [{ currency: 'TRY', totalBalance: '5000' }],
      monthlyAverageExpense: '1000',
      monthsCovered: '5.0',
      status: 'amber',
      includedTypes: ['BANK_SAVINGS'],
      sampleMonths: 12,
    });
    const { client, Wrapper } = createWrapper();

    const { result } = renderHook(() => useEmergencyFund(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.data?.status).toBe('amber'));
    expect(client.getQueryData(['dashboard', 'emergency-fund'])).toBeDefined();
  });

  it('updates the cache after successful mutation', async () => {
    vi.mocked(dashboardApi.updateEmergencyFundTypes).mockResolvedValueOnce({
      currentReserve: '7500',
      buckets: [{ currency: 'TRY', totalBalance: '7500' }],
      monthlyAverageExpense: '1000',
      monthsCovered: '7.5',
      status: 'green',
      includedTypes: ['BANK_SAVINGS', 'CASH'],
      sampleMonths: 12,
    });
    const { client, Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateEmergencyFundTypes(), { wrapper: Wrapper });

    await result.current.mutateAsync({ types: ['BANK_SAVINGS', 'CASH'] });

    await waitFor(() => {
      const cached = client.getQueryData<{ status: string }>(['dashboard', 'emergency-fund']);
      expect(cached?.status).toBe('green');
    });
  });

  it('handles the insufficient-data response shape', async () => {
    vi.mocked(dashboardApi.emergencyFund).mockResolvedValueOnce({
      currentReserve: '500',
      buckets: [],
      monthlyAverageExpense: '0',
      monthsCovered: null,
      status: 'insufficient-data',
      includedTypes: ['BANK_SAVINGS'],
      sampleMonths: 1,
    });
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useEmergencyFund(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.data?.status).toBe('insufficient-data'));
    expect(result.current.data?.monthsCovered).toBeNull();
    expect(result.current.data?.sampleMonths).toBe(1);
  });
});
