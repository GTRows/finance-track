import {
  Briefcase,
  PiggyBank,
  Shield,
  TrendingUp,
  Bitcoin,
  Building2,
  Wallet,
  Package,
  type LucideIcon,
} from 'lucide-react';
import type { PortfolioType } from '@/types/portfolio.types';

export interface PortfolioTypeMeta {
  value: PortfolioType;
  /** Translation key under `portfolioTypes.*` — render with t(). */
  labelKey: string;
  icon: LucideIcon;
  /** Tailwind classes for the colored badge background and text. */
  badgeClass: string;
}

export const PORTFOLIO_TYPES: PortfolioTypeMeta[] = [
  {
    value: 'INDIVIDUAL',
    labelKey: 'portfolioTypes.INDIVIDUAL',
    icon: Wallet,
    badgeClass: 'bg-primary/10 text-primary',
  },
  {
    value: 'BES',
    labelKey: 'portfolioTypes.BES',
    icon: PiggyBank,
    badgeClass: 'bg-blue-500/10 text-blue-400',
  },
  {
    value: 'RETIREMENT',
    labelKey: 'portfolioTypes.RETIREMENT',
    icon: Shield,
    badgeClass: 'bg-purple-500/10 text-purple-400',
  },
  {
    value: 'EMERGENCY',
    labelKey: 'portfolioTypes.EMERGENCY',
    icon: Briefcase,
    badgeClass: 'bg-orange-500/10 text-orange-400',
  },
  {
    value: 'STOCKS',
    labelKey: 'portfolioTypes.STOCKS',
    icon: TrendingUp,
    badgeClass: 'bg-emerald-500/10 text-emerald-400',
  },
  {
    value: 'CRYPTO',
    labelKey: 'portfolioTypes.CRYPTO',
    icon: Bitcoin,
    badgeClass: 'bg-yellow-500/10 text-yellow-400',
  },
  {
    value: 'REAL_ESTATE',
    labelKey: 'portfolioTypes.REAL_ESTATE',
    icon: Building2,
    badgeClass: 'bg-cyan-500/10 text-cyan-400',
  },
  {
    value: 'OTHER',
    labelKey: 'portfolioTypes.OTHER',
    icon: Package,
    badgeClass: 'bg-muted text-muted-foreground',
  },
];

export function getPortfolioTypeMeta(type: PortfolioType): PortfolioTypeMeta {
  return PORTFOLIO_TYPES.find((t) => t.value === type) ?? PORTFOLIO_TYPES[PORTFOLIO_TYPES.length - 1];
}
