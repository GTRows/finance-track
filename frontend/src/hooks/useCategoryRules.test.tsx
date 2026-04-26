import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { categoryRulesApi } from '@/api/category-rules.api';
import {
  useCategoryRules,
  useCreateCategoryRule,
  useDeleteCategoryRule,
  useUpdateCategoryRule,
} from './useCategoryRules';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/category-rules.api', () => ({
  categoryRulesApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('useCategoryRules hooks', () => {
  beforeEach(() => {
    Object.values(categoryRulesApi).forEach((fn) => vi.mocked(fn).mockReset());
  });

  it('useCategoryRules returns the list', async () => {
    vi.mocked(categoryRulesApi.list).mockResolvedValueOnce([] as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useCategoryRules(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useCreateCategoryRule invalidates rules on success', async () => {
    vi.mocked(categoryRulesApi.create).mockResolvedValueOnce({ id: 'r1' } as never);
    const { Wrapper, client } = createWrapper();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    const { result } = renderHook(() => useCreateCategoryRule(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ pattern: 'x' } as never);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['budget', 'category-rules'] });
  });

  it('useUpdateCategoryRule forwards id and request', async () => {
    vi.mocked(categoryRulesApi.update).mockResolvedValueOnce({ id: 'r1' } as never);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useUpdateCategoryRule(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({ id: 'r1', req: { pattern: 'y' } as never });
    });

    expect(categoryRulesApi.update).toHaveBeenCalledWith('r1', { pattern: 'y' });
  });

  it('useDeleteCategoryRule passes id', async () => {
    vi.mocked(categoryRulesApi.delete).mockResolvedValueOnce(undefined);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useDeleteCategoryRule(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync('r1');
    });

    expect(categoryRulesApi.delete).toHaveBeenCalledWith('r1');
  });
});
