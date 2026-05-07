/** Owner-scoped account type. Mirrors the backend `Account.AccountType` enum. */
export type AccountType =
  | 'BANK_CHECKING'
  | 'BANK_SAVINGS'
  | 'BROKERAGE_CASH'
  | 'CRYPTO_WALLET'
  | 'CASH'
  | 'OTHER';

/** Account row returned by the list / get / create / update endpoints. */
export interface Account {
  id: string;
  name: string;
  type: AccountType;
  currency: string;
  institution: string | null;
  accountNumberSuffix: string | null;
  notes: string | null;
  /** BigDecimal serialised as string to preserve precision (8-decimal scale). */
  currentBalance: string;
  archived: boolean;
  createdAt: string;
}

/** Request body for creating a new account. */
export interface CreateAccountRequest {
  name: string;
  type: AccountType;
  currency: string;
  institution?: string | null;
  accountNumberSuffix?: string | null;
  notes?: string | null;
  /** Optional opening balance; backend treats null as zero. */
  openingBalance?: string | null;
}

/** Request body for updating an existing account. Type is immutable. */
export interface UpdateAccountRequest {
  name: string;
  currency: string;
  institution?: string | null;
  accountNumberSuffix?: string | null;
  notes?: string | null;
  currentBalance: string;
}

/** Aggregate balance rollup returned by the totals endpoint. */
export interface AccountTotalsResponse {
  liveCount: number;
  archivedCount: number;
  byCurrency: CurrencyTotal[];
}

/** Single per-currency rollup row inside the totals response. */
export interface CurrencyTotal {
  currency: string;
  totalBalance: string;
}
