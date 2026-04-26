import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { portfolioApi } from '@/api/portfolio.api';
import {
  useCreatePortfolio,
  useDeletePortfolio,
  usePortfolio,
  usePortfolios,
  useUpdatePortfolio,
} from './usePortfolios';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/portfolio.api', () => ({
  portfolioApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('usePortfolios hooks', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.list).mockReset();
    vi.mocked(portfolioApi.get).mockReset();
    vi.mocked(portfolioApi.create).mockReset();
    vi.mocked(portfolioApi.update).mockReset();
    vi.mocked(portfolioApi.delete).mockReset();
  });

  it('usePortfolios returns the list', async () => {
    vi.mocked(portfolioApi.list).mockResolvedValueOnce([
      { id: 'a', name: 'Main' } as never,
    ]);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => usePortfolios(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
  });

  it('usePortfolio is disabled until id is provided', async () => {
    const { Wrapper } = createWrapper();
    const initialProps: { id: string | undefined } = { id: undefined };
    const { result, rerender } = renderHook(({ id }: { id: string | undefined }) => usePortfolio(id), {
      wrapper: Wrapper,
      initialProps,
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(portfolioApi.get).not.toHaveBeenCalled();

    vi.mocked(portfolioApi.get).mockResolvedValueOnce({ id: 'p1', name: 'P1' } as never);
    rerender({ id: 'p1' });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(portfolioApi.get).toHaveBeenCalledWith('p1');
  });

  it('useCreatePortfolio invalidates the portfolios cache on success', async () => {
    vi.mocked(portfolioApi.create).mockResolvedValueOnce({ id: 'new', name: 'New' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreatePortfolio(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ name: 'New' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['portfolios'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
  });

  it('useUpdatePortfolio passes id and request through', async () => {
    vi.mocked(portfolioApi.update).mockResolvedValueOnce({ id: 'p1', name: 'Renamed' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdatePortfolio(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'p1', request: { name: 'Renamed' } as never });
    });

    expect(portfolioApi.update).toHaveBeenCalledWith('p1', { name: 'Renamed' });
  });

  it('useDeletePortfolio invalidates after delete', async () => {
    vi.mocked(portfolioApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useDeletePortfolio(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('p1');
    });

    expect(portfolioApi.delete).toHaveBeenCalledWith('p1');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['portfolios'] });
  });
});
