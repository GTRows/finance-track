import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { alertsApi, notificationsApi } from '@/api/alerts.api';
import type { CreateAlertRequest } from '@/types/alert.types';

const alertsKey = () => ['alerts'] as const;
const notificationsKey = () => ['notifications'] as const;
const unreadKey = () => ['notifications', 'unread'] as const;

export function useAlerts() {
  return useQuery({
    queryKey: alertsKey(),
    queryFn: alertsApi.list,
  });
}

export function useCreateAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateAlertRequest) => alertsApi.create(req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: alertsKey() });
    },
  });
}

export function useDeleteAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => alertsApi.delete(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: alertsKey() });
    },
  });
}

export function useNotifications() {
  return useQuery({
    queryKey: notificationsKey(),
    queryFn: notificationsApi.list,
    refetchInterval: 30_000,
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: unreadKey(),
    queryFn: notificationsApi.unreadCount,
    refetchInterval: 30_000,
  });
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => notificationsApi.markRead(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: notificationsKey() });
      void qc.invalidateQueries({ queryKey: unreadKey() });
    },
  });
}

export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => notificationsApi.markAllRead(),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: notificationsKey() });
      void qc.invalidateQueries({ queryKey: unreadKey() });
    },
  });
}
