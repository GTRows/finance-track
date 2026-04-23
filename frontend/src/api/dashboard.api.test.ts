import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { dashboardApi } from './dashboard.api';
import client from './client';

describe('dashboardApi', () => {
  let getSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    getSpy = vi.spyOn(client, 'get');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('get calls /dashboard and returns the data payload', async () => {
    const payload = {
      totalNetWorth: 1000,
      portfolios: [],
      budget: { period: '2026-04', income: 0, expense: 0, net: 0, savingsRate: 0 },
      upcomingBills: [],
    };
    getSpy.mockResolvedValue({ data: payload } as never);

    const res = await dashboardApi.get();

    expect(getSpy).toHaveBeenCalledWith('/dashboard');
    expect(res).toEqual(payload);
  });

  it('get propagates axios rejection', async () => {
    getSpy.mockRejectedValue(new Error('network down'));

    await expect(dashboardApi.get()).rejects.toThrow('network down');
  });
});
