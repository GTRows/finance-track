import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { debtsApi } from '@/api/debts.api';
import type { DebtPaymentRequest, UpsertDebtRequest } from '@/types/debt.types';

const debtsKey = () => ['debts'] as const;
const paymentsKey = (id: string) => ['debts', id, 'payments'] as const;

export function useDebts() {
  return useQuery({
    queryKey: debtsKey(),
    queryFn: debtsApi.list,
    staleTime: 60_000,
  });
}

export function useCreateDebt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertDebtRequest) => debtsApi.create(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: debtsKey() }),
  });
}

export function useUpdateDebt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: UpsertDebtRequest }) =>
      debtsApi.update(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: debtsKey() }),
  });
}

export function useArchiveDebt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => debtsApi.archive(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: debtsKey() }),
  });
}

export function useDebtPayments(id: string | null) {
  return useQuery({
    queryKey: paymentsKey(id ?? ''),
    queryFn: () => debtsApi.payments(id!),
    enabled: !!id,
    staleTime: 30_000,
  });
}

export function useAddDebtPayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: DebtPaymentRequest }) =>
      debtsApi.addPayment(id, req),
    onSuccess: (_data, { id }) => {
      void qc.invalidateQueries({ queryKey: debtsKey() });
      void qc.invalidateQueries({ queryKey: paymentsKey(id) });
    },
  });
}

export function useDeleteDebtPayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, paymentId }: { id: string; paymentId: string }) =>
      debtsApi.deletePayment(id, paymentId),
    onSuccess: (_data, { id }) => {
      void qc.invalidateQueries({ queryKey: debtsKey() });
      void qc.invalidateQueries({ queryKey: paymentsKey(id) });
    },
  });
}
