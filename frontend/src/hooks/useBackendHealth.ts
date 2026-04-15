import { useQuery } from '@tanstack/react-query';
import client from '@/api/client';

async function checkHealth(): Promise<boolean> {
  try {
    const res = await client.get('/health', { timeout: 3000 });
    return res.status >= 200 && res.status < 300;
  } catch {
    return false;
  }
}

export function useBackendHealth() {
  return useQuery({
    queryKey: ['backend-health'],
    queryFn: checkHealth,
    refetchInterval: (q) => (q.state.data === false ? 5_000 : 30_000),
    refetchOnWindowFocus: true,
    retry: false,
    staleTime: 0,
  });
}
