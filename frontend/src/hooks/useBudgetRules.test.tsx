import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { budgetRulesApi } from '@/api/budget-rules.api';
import {
  useBudgetRules,
  useCreateBudgetRule,
  useDeleteBudgetRule,
} from './useBudgetRules';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/budget-rules.api', () => ({
  budgetRulesApi: { list: vi.fn(), create: vi.fn(), delete: vi.fn() },
}));

describe('useBudgetRules hooks', () => {
  beforeEach(() => {
    Object.values(budgetRulesApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useBudgetRules returns the list', async () => {
    vi.mocked(budgetRulesApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useBudgetRules(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useCreateBudgetRule invalidates rules', async () => {
    vi.mocked(budgetRulesApi.create).mockResolvedValueOnce({ id: 'r1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateBudgetRule(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ categoryId: 'c1', monthlyLimitTry: 100 } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['budget', 'rules'] });
  });

  it('useDeleteBudgetRule passes the id', async () => {
    vi.mocked(budgetRulesApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteBudgetRule(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('r1');
    });

    expect(budgetRulesApi.delete).toHaveBeenCalledWith('r1');
  });
});
