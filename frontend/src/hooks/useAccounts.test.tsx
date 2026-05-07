import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { accountsApi } from '@/api/accounts.api';
import {
  useAccounts,
  useArchiveAccount,
  useCreateAccount,
} from './useAccounts';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/accounts.api', () => ({
  accountsApi: {
    list: vi.fn(),
    totals: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('useAccounts hooks', () => {
  beforeEach(() => {
    vi.mocked(accountsApi.list).mockReset();
    vi.mocked(accountsApi.totals).mockReset();
    vi.mocked(accountsApi.create).mockReset();
    vi.mocked(accountsApi.delete).mockReset();
  });

  it('useAccounts queries the list endpoint and caches under the accounts key', async () => {
    vi.mocked(accountsApi.list).mockResolvedValueOnce([
      { id: 'a', name: 'Main', currency: 'TRY' } as never,
    ]);

    const { client, Wrapper } = createWrapper();
    const { result } = renderHook(() => useAccounts(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(accountsApi.list).toHaveBeenCalledTimes(1);
    expect(client.getQueryData(['accounts'])).toBeDefined();
  });

  it('useCreateAccount invalidates the accounts list and totals on success', async () => {
    vi.mocked(accountsApi.create).mockResolvedValueOnce({
      id: 'new',
      name: 'New',
      currency: 'TRY',
    } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateAccount(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({
        name: 'New',
        type: 'BANK_CHECKING',
        currency: 'TRY',
      } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['accounts', 'totals'] });
  });

  it('useArchiveAccount invalidates both the list and totals on success', async () => {
    vi.mocked(accountsApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useArchiveAccount(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('a1');
    });

    expect(accountsApi.delete).toHaveBeenCalledWith('a1');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['accounts', 'totals'] });
  });
});
