import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { dashboardApi } from '@/api/dashboard.api';
import type {
  EmergencyFundResponse,
  UpdateEmergencyFundConfigRequest,
  UpdateEmergencyFundTypesRequest,
} from '@/types/emergency-fund.types';

const EMERGENCY_FUND_KEY = ['dashboard', 'emergency-fund'] as const;

/** Fetches the emergency-fund coverage rollup for the dashboard tile. */
export function useEmergencyFund() {
  return useQuery<EmergencyFundResponse>({
    queryKey: EMERGENCY_FUND_KEY,
    queryFn: dashboardApi.emergencyFund,
    staleTime: 60_000,
  });
}

/** Persists the operator's preferred set of included account types. */
export function useUpdateEmergencyFundTypes() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateEmergencyFundTypesRequest) =>
      dashboardApi.updateEmergencyFundTypes(body),
    onSuccess: (data) => {
      qc.setQueryData(EMERGENCY_FUND_KEY, data);
    },
  });
}

/** Persists the full emergency-fund configuration (types + target + amber floor). */
export function useUpdateEmergencyFundConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateEmergencyFundConfigRequest) =>
      dashboardApi.updateEmergencyFundConfig(body),
    onSuccess: (data) => {
      qc.setQueryData(EMERGENCY_FUND_KEY, data);
    },
  });
}
