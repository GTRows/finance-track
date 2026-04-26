import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { riskApi } from '@/api/risk.api';
import { useRisk } from './useRisk';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/risk.api', () => ({
  riskApi: { get: vi.fn() },
}));

describe('useRisk', () => {
  beforeEach(() => {
    vi.mocked(riskApi.get).mockReset();
  });

  it('is disabled without portfolioId', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useRisk(undefined), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
  });

  it('passes portfolioId and riskFreeRate', async () => {
    vi.mocked(riskApi.get).mockResolvedValueOnce({ sharpeRatio: 0.5 } as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useRisk('p1', 0.3), { wrapper: Wrapper });
    await waitFor(() => expect(riskApi.get).toHaveBeenCalledWith('p1', 0.3));
  });
});
