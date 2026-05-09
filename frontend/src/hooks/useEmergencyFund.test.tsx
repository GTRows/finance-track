import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { dashboardApi } from '@/api/dashboard.api';
import {
  useEmergencyFund,
  useUpdateEmergencyFundConfig,
  useUpdateEmergencyFundTypes,
} from './useEmergencyFund';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/dashboard.api', () => ({
  dashboardApi: {
    emergencyFund: vi.fn(),
    updateEmergencyFundTypes: vi.fn(),
    updateEmergencyFundConfig: vi.fn(),
  },
}));

describe('useEmergencyFund', () => {
  beforeEach(() => {
    vi.mocked(dashboardApi.emergencyFund).mockReset();
    vi.mocked(dashboardApi.updateEmergencyFundTypes).mockReset();
    vi.mocked(dashboardApi.updateEmergencyFundConfig).mockReset();
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
      targetMonths: 6,
      amberFloorMonths: 3,
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
      targetMonths: 6,
      amberFloorMonths: 3,
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
      targetMonths: 6,
      amberFloorMonths: 3,
    });
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useEmergencyFund(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.data?.status).toBe('insufficient-data'));
    expect(result.current.data?.monthsCovered).toBeNull();
    expect(result.current.data?.sampleMonths).toBe(1);
  });

  it('updates the cache after config mutation', async () => {
    vi.mocked(dashboardApi.updateEmergencyFundConfig).mockResolvedValueOnce({
      currentReserve: '8000',
      buckets: [{ currency: 'TRY', totalBalance: '8000' }],
      monthlyAverageExpense: '1000',
      monthsCovered: '8.0',
      status: 'amber',
      includedTypes: ['BANK_SAVINGS'],
      sampleMonths: 12,
      targetMonths: 9,
      amberFloorMonths: 4,
    });
    const { client, Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateEmergencyFundConfig(), { wrapper: Wrapper });

    await result.current.mutateAsync({
      types: ['BANK_SAVINGS'],
      targetMonths: 9,
      amberFloorMonths: 4,
    });

    await waitFor(() => {
      const cached = client.getQueryData<{ targetMonths: number; amberFloorMonths: number }>([
        'dashboard',
        'emergency-fund',
      ]);
      expect(cached?.targetMonths).toBe(9);
      expect(cached?.amberFloorMonths).toBe(4);
    });
    expect(dashboardApi.updateEmergencyFundConfig).toHaveBeenCalledWith({
      types: ['BANK_SAVINGS'],
      targetMonths: 9,
      amberFloorMonths: 4,
    });
  });

  it('legacy types-only mutation continues to work after config endpoint exists', async () => {
    vi.mocked(dashboardApi.updateEmergencyFundTypes).mockResolvedValueOnce({
      currentReserve: '6000',
      buckets: [{ currency: 'TRY', totalBalance: '6000' }],
      monthlyAverageExpense: '1000',
      monthsCovered: '6.0',
      status: 'amber',
      includedTypes: ['BANK_SAVINGS', 'CASH'],
      sampleMonths: 12,
      targetMonths: 6,
      amberFloorMonths: 3,
    });
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateEmergencyFundTypes(), { wrapper: Wrapper });

    await result.current.mutateAsync({ types: ['BANK_SAVINGS', 'CASH'] });

    expect(dashboardApi.updateEmergencyFundTypes).toHaveBeenCalledWith({
      types: ['BANK_SAVINGS', 'CASH'],
    });
    expect(dashboardApi.updateEmergencyFundConfig).not.toHaveBeenCalled();
  });
});
