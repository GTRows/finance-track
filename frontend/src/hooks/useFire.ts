import { useQuery } from '@tanstack/react-query';
import { fireApi } from '@/api/fire.api';
import type { FireQuery } from '@/types/fire.types';

export function useFire(query: FireQuery) {
  return useQuery({
    queryKey: ['fire', query] as const,
    queryFn: () => fireApi.compute(query),
    staleTime: 60_000,
  });
}
