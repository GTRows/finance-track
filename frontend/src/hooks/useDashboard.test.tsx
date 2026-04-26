import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { dashboardApi } from '@/api/dashboard.api';
import { useDashboard } from './useDashboard';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/dashboard.api', () => ({
  dashboardApi: { get: vi.fn() },
}));

describe('useDashboard', () => {
  beforeEach(() => {
    vi.mocked(dashboardApi.get).mockReset();
  });

  it('returns the api payload on success', async () => {
    const payload = {
      totalValueTry: 1000,
      portfolios: [],
      monthlyIncome: 5000,
      monthlyExpense: 3000,
    };
    vi.mocked(dashboardApi.get).mockResolvedValueOnce(payload as never);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useDashboard(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(payload);
    expect(dashboardApi.get).toHaveBeenCalledOnce();
  });

  it('surfaces the error when the api rejects', async () => {
    vi.mocked(dashboardApi.get).mockRejectedValueOnce(new Error('boom'));

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useDashboard(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect((result.current.error as Error).message).toBe('boom');
  });
});
