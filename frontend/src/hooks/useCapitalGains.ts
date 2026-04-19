import { useQuery } from '@tanstack/react-query';
import { capitalGainsApi, type CapitalGainsReport } from '@/api/capitalgains.api';

export function useCapitalGains(year?: number | null) {
  return useQuery<CapitalGainsReport>({
    queryKey: ['reports', 'capitalGains', year ?? 'all'],
    queryFn: () => capitalGainsApi.fetch(year),
    staleTime: 60_000,
  });
}
