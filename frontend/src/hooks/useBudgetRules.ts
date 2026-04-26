import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { budgetRulesApi } from '@/api/budget-rules.api';
import type { CreateBudgetRuleRequest } from '@/types/budget-rule.types';

const rulesKey = () => ['budget', 'rules'] as const;

export function useBudgetRules() {
  return useQuery({
    queryKey: rulesKey(),
    queryFn: budgetRulesApi.list,
  });
}

export function useCreateBudgetRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateBudgetRuleRequest) => budgetRulesApi.create(req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: rulesKey() });
    },
  });
}

export function useDeleteBudgetRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => budgetRulesApi.delete(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: rulesKey() });
    },
  });
}
