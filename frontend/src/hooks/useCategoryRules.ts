import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { categoryRulesApi } from '@/api/category-rules.api';
import type { UpsertCategoryRuleRequest } from '@/types/category-rule.types';

const rulesKey = () => ['budget', 'category-rules'] as const;

export function useCategoryRules() {
  return useQuery({
    queryKey: rulesKey(),
    queryFn: categoryRulesApi.list,
  });
}

export function useCreateCategoryRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertCategoryRuleRequest) => categoryRulesApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: rulesKey() });
    },
  });
}

export function useUpdateCategoryRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: UpsertCategoryRuleRequest }) =>
      categoryRulesApi.update(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: rulesKey() });
    },
  });
}

export function useDeleteCategoryRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => categoryRulesApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: rulesKey() });
    },
  });
}
