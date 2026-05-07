import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { taxTrApi } from '@/api/taxtr.api';
import { useTrTaxReport } from './useTrTaxReport';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/taxtr.api', () => ({
  taxTrApi: { fetch: vi.fn() },
}));

describe('useTrTaxReport', () => {
  beforeEach(() => {
    vi.mocked(taxTrApi.fetch).mockReset();
  });

  it('passes year to the api and caches under the year key', async () => {
    vi.mocked(taxTrApi.fetch).mockResolvedValueOnce({
      year: 2025,
      parameters: null,
      dividendStoppage: {
        totalGrossTry: 0,
        totalWithholdingTry: 0,
        totalNetTry: 0,
        byAsset: [],
      },
      capitalGains: {
        realizedTry: 0,
        thresholdTry: null,
        headroomTry: null,
        usedRatio: null,
        status: 'unknown',
      },
      warnings: [],
    });
    const { client, Wrapper } = createWrapper();

    const { result } = renderHook(() => useTrTaxReport(2025), { wrapper: Wrapper });

    await waitFor(() => expect(taxTrApi.fetch).toHaveBeenCalledWith(2025));
    await waitFor(() => expect(result.current.data?.year).toBe(2025));
    expect(client.getQueryData(['reports', 'tax', 'tr', 2025])).toBeDefined();
  });

  it('falls back to "current" key when year is omitted', async () => {
    vi.mocked(taxTrApi.fetch).mockResolvedValueOnce({} as never);
    const { client, Wrapper } = createWrapper();

    renderHook(() => useTrTaxReport(), { wrapper: Wrapper });

    await waitFor(() => expect(taxTrApi.fetch).toHaveBeenCalledWith(undefined));
    await waitFor(() =>
      expect(client.getQueryData(['reports', 'tax', 'tr', 'current'])).toBeDefined(),
    );
  });
});
