import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { capitalGainsApi } from '@/api/capitalgains.api';
import { useCapitalGains } from './useCapitalGains';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/capitalgains.api', () => ({
  capitalGainsApi: { fetch: vi.fn() },
}));

describe('useCapitalGains', () => {
  beforeEach(() => {
    vi.mocked(capitalGainsApi.fetch).mockReset();
  });

  it('passes year to the api when provided', async () => {
    vi.mocked(capitalGainsApi.fetch).mockResolvedValueOnce({ realizedGain: 100 } as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useCapitalGains(2025), { wrapper: Wrapper });
    await waitFor(() => expect(capitalGainsApi.fetch).toHaveBeenCalledWith(2025));
  });

  it('passes null/undefined year through to the api', async () => {
    vi.mocked(capitalGainsApi.fetch).mockResolvedValueOnce({} as never);
    const { Wrapper } = createWrapper();

    renderHook(() => useCapitalGains(), { wrapper: Wrapper });
    await waitFor(() => expect(capitalGainsApi.fetch).toHaveBeenCalledWith(undefined));
  });
});
