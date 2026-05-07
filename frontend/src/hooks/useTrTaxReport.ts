import { useQuery } from '@tanstack/react-query';
import { taxTrApi } from '@/api/taxtr.api';
import type { TrTaxReport } from '@/types/tax.types';

export function useTrTaxReport(year?: number | null) {
  return useQuery<TrTaxReport>({
    queryKey: ['reports', 'tax', 'tr', year ?? 'current'],
    queryFn: () => taxTrApi.fetch(year),
    staleTime: 60_000,
  });
}
