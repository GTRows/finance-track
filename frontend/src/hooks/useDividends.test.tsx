import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { dividendApi } from '@/api/dividend.api';
import { useDeleteDividend, useDividends, useRecordDividend } from './useDividends';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/dividend.api', () => ({
  dividendApi: {
    listForPortfolio: vi.fn(),
    record: vi.fn(),
    remove: vi.fn(),
  },
}));

describe('useDividends hooks', () => {
  beforeEach(() => {
    Object.values(dividendApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useDividends is disabled without portfolioId', () => {
    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useDividends(undefined), { wrapper: Wrapper });

    expect(result.current.fetchStatus).toBe('idle');
  });

  it('useDividends returns the list', async () => {
    vi.mocked(dividendApi.listForPortfolio).mockResolvedValueOnce([{ id: 'd1' } as never]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDividends('p1'), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
  });

  it('useRecordDividend invalidates dividends + dashboard', async () => {
    vi.mocked(dividendApi.record).mockResolvedValueOnce({ id: 'd1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useRecordDividend('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ assetId: 'a1' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['portfolios', 'p1', 'dividends'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
  });

  it('useDeleteDividend forwards the dividend id', async () => {
    vi.mocked(dividendApi.remove).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteDividend('p1'), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('d1');
    });

    expect(dividendApi.remove).toHaveBeenCalledWith('p1', 'd1');
  });
});
