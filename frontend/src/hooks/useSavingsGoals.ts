import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { savingsApi } from '@/api/savings.api';
import type {
  SavingsContributionRequest,
  UpsertSavingsGoalRequest,
} from '@/types/savings.types';

const goalsKey = () => ['savings', 'goals'] as const;
const contributionsKey = (id: string) => ['savings', 'goals', id, 'contributions'] as const;

export function useSavingsGoals() {
  return useQuery({
    queryKey: goalsKey(),
    queryFn: savingsApi.list,
    staleTime: 60_000,
  });
}

export function useCreateSavingsGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertSavingsGoalRequest) => savingsApi.create(req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: goalsKey() });
    },
  });
}

export function useUpdateSavingsGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: UpsertSavingsGoalRequest }) =>
      savingsApi.update(id, req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: goalsKey() });
    },
  });
}

export function useArchiveSavingsGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => savingsApi.archive(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: goalsKey() });
    },
  });
}

export function useSavingsContributions(id: string | null) {
  return useQuery({
    queryKey: contributionsKey(id ?? ''),
    queryFn: () => savingsApi.contributions(id!),
    enabled: !!id,
    staleTime: 30_000,
  });
}

export function useAddSavingsContribution() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: SavingsContributionRequest }) =>
      savingsApi.addContribution(id, req),
    onSuccess: (_data, { id }) => {
      void qc.invalidateQueries({ queryKey: goalsKey() });
      void qc.invalidateQueries({ queryKey: contributionsKey(id) });
    },
  });
}

export function useDeleteSavingsContribution() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, contributionId }: { id: string; contributionId: string }) =>
      savingsApi.deleteContribution(id, contributionId),
    onSuccess: (_data, { id }) => {
      void qc.invalidateQueries({ queryKey: goalsKey() });
      void qc.invalidateQueries({ queryKey: contributionsKey(id) });
    },
  });
}
