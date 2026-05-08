import type { AccountType } from './account.types';

/** Dashboard tile rollup. Status string maps to red/amber/green/insufficient bands. */
export interface EmergencyFundResponse {
  /** BigDecimal serialised as string to preserve precision (face-value across currencies). */
  currentReserve: string;
  buckets: Array<{ currency: string; totalBalance: string }>;
  monthlyAverageExpense: string;
  monthsCovered: string | null;
  status: 'red' | 'amber' | 'green' | 'insufficient-data';
  includedTypes: AccountType[];
  sampleMonths: number;
}

export interface UpdateEmergencyFundTypesRequest {
  types: AccountType[];
}
