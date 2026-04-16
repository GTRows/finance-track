import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { netWorthApi } from '@/api/networth.api';
import type { UpsertEventRequest } from '@/types/networth.types';

const timelineKey = () => ['net-worth', 'timeline'] as const;

export function useNetWorth() {
  return useQuery({
    queryKey: timelineKey(),
    queryFn: netWorthApi.timeline,
    staleTime: 60_000,
  });
}

export function useCreateNetWorthEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertEventRequest) => netWorthApi.createEvent(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: timelineKey() });
    },
  });
}

export function useUpdateNetWorthEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: UpsertEventRequest }) =>
      netWorthApi.updateEvent(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: timelineKey() });
    },
  });
}

export function useDeleteNetWorthEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => netWorthApi.deleteEvent(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: timelineKey() });
    },
  });
}
