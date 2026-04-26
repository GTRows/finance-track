import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { tagsApi } from '@/api/tags.api';
import type { UpsertTagRequest } from '@/types/tag.types';

const tagsKey = () => ['tags'] as const;

export function useTags() {
  return useQuery({
    queryKey: tagsKey(),
    queryFn: tagsApi.list,
    staleTime: 5 * 60_000,
  });
}

export function useCreateTag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertTagRequest) => tagsApi.create(req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: tagsKey() });
      void qc.invalidateQueries({ queryKey: ['budget', 'transactions'] });
    },
  });
}

export function useUpdateTag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: UpsertTagRequest }) =>
      tagsApi.update(id, req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: tagsKey() });
      void qc.invalidateQueries({ queryKey: ['budget', 'transactions'] });
    },
  });
}

export function useDeleteTag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => tagsApi.remove(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: tagsKey() });
      void qc.invalidateQueries({ queryKey: ['budget', 'transactions'] });
    },
  });
}
