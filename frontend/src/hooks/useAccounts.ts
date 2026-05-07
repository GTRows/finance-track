import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountsApi } from '@/api/accounts.api';
import type { CreateAccountRequest, UpdateAccountRequest } from '@/types/account.types';

const ACCOUNTS_KEY = ['accounts'] as const;
const ACCOUNTS_TOTALS_KEY = ['accounts', 'totals'] as const;

/** Fetches the list of live accounts for the authenticated user. */
export function useAccounts() {
  return useQuery({
    queryKey: ACCOUNTS_KEY,
    queryFn: accountsApi.list,
  });
}

/** Fetches a single account by id. */
export function useAccount(id: string | undefined) {
  return useQuery({
    queryKey: ['accounts', id],
    queryFn: () => accountsApi.get(id as string),
    enabled: !!id,
  });
}

/** Fetches the aggregate totals (counts + per-currency rollup). */
export function useAccountTotals() {
  return useQuery({
    queryKey: ACCOUNTS_TOTALS_KEY,
    queryFn: accountsApi.totals,
  });
}

/** Creates a new account and refreshes the list + totals. */
export function useCreateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateAccountRequest) => accountsApi.create(request),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ACCOUNTS_KEY });
      void qc.invalidateQueries({ queryKey: ACCOUNTS_TOTALS_KEY });
    },
  });
}

/** Updates an existing account and refreshes the list + totals. */
export function useUpdateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateAccountRequest }) =>
      accountsApi.update(id, request),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ACCOUNTS_KEY });
      void qc.invalidateQueries({ queryKey: ACCOUNTS_TOTALS_KEY });
    },
  });
}

/** Soft-archives an account and refreshes the list + totals. */
export function useArchiveAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => accountsApi.delete(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ACCOUNTS_KEY });
      void qc.invalidateQueries({ queryKey: ACCOUNTS_TOTALS_KEY });
    },
  });
}
