import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { billsApi } from '@/api/bills.api';
import type { CreateBillRequest, PayBillRequest } from '@/types/bill.types';

const billsKey = () => ['bills'] as const;

export function useBills() {
  return useQuery({
    queryKey: billsKey(),
    queryFn: billsApi.list,
  });
}

export function useCreateBill() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateBillRequest) => billsApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: billsKey() });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useDeleteBill() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => billsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: billsKey() });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function usePayBill() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: PayBillRequest }) => billsApi.pay(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: billsKey() });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
