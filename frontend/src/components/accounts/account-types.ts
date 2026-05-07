import {
  Banknote,
  Coins,
  CreditCard,
  PiggyBank,
  Wallet,
  Landmark,
  type LucideIcon,
} from 'lucide-react';
import type { AccountType } from '@/types/account.types';

/** Visual + i18n metadata for an account type. */
export interface AccountTypeMeta {
  value: AccountType;
  icon: LucideIcon;
  labelKey: string;
  badgeClass: string;
}

/** Ordered list of supported account types. */
export const ACCOUNT_TYPES: ReadonlyArray<AccountTypeMeta> = [
  {
    value: 'BANK_CHECKING',
    icon: CreditCard,
    labelKey: 'accounts.types.BANK_CHECKING',
    badgeClass: 'bg-sky-500/15 text-sky-700 dark:text-sky-300',
  },
  {
    value: 'BANK_SAVINGS',
    icon: PiggyBank,
    labelKey: 'accounts.types.BANK_SAVINGS',
    badgeClass: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
  },
  {
    value: 'BROKERAGE_CASH',
    icon: Landmark,
    labelKey: 'accounts.types.BROKERAGE_CASH',
    badgeClass: 'bg-violet-500/15 text-violet-700 dark:text-violet-300',
  },
  {
    value: 'CRYPTO_WALLET',
    icon: Coins,
    labelKey: 'accounts.types.CRYPTO_WALLET',
    badgeClass: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
  },
  {
    value: 'CASH',
    icon: Banknote,
    labelKey: 'accounts.types.CASH',
    badgeClass: 'bg-stone-500/15 text-stone-700 dark:text-stone-300',
  },
  {
    value: 'OTHER',
    icon: Wallet,
    labelKey: 'accounts.types.OTHER',
    badgeClass: 'bg-muted text-muted-foreground',
  },
];

/** Returns the metadata entry for a given account type, falling back to OTHER. */
export function getAccountTypeMeta(type: AccountType): AccountTypeMeta {
  return ACCOUNT_TYPES.find((t) => t.value === type) ?? ACCOUNT_TYPES[ACCOUNT_TYPES.length - 1];
}
