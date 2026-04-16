import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { recurringApi } from '@/api/recurring.api';
import type { UpsertRecurringRequest } from '@/types/recurring.types';

const key = () => ['recurring-templates'] as const;

export function useRecurringTemplates() {
  return useQuery({
    queryKey: key(),
    queryFn: recurringApi.list,
  });
}

export function useCreateRecurring() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertRecurringRequest) => recurringApi.create(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: key() }),
  });
}

export function useUpdateRecurring() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: UpsertRecurringRequest }) =>
      recurringApi.update(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: key() }),
  });
}

export function useDeleteRecurring() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => recurringApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: key() }),
  });
}

export function useRunRecurring() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => recurringApi.runNow(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: key() });
      qc.invalidateQueries({ queryKey: ['budget'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
