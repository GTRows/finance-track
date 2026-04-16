import { useQuery } from '@tanstack/react-query';
import { riskApi } from '@/api/risk.api';

const riskKey = (portfolioId: string, riskFreeRate?: number) =>
  ['portfolios', portfolioId, 'risk', riskFreeRate ?? 0] as const;

export function useRisk(portfolioId: string | undefined, riskFreeRate?: number) {
  return useQuery({
    queryKey: riskKey(portfolioId ?? '', riskFreeRate),
    queryFn: () => riskApi.get(portfolioId as string, riskFreeRate),
    enabled: !!portfolioId,
    staleTime: 60_000,
  });
}
