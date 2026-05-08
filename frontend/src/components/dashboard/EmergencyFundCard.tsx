import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { Shield, ShieldAlert, ShieldCheck, Wallet, TrendingDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatTRY } from '@/utils/formatters';
import { useEmergencyFund, useUpdateEmergencyFundTypes } from '@/hooks/useEmergencyFund';
import type { AccountType } from '@/types/account.types';

const TOGGLEABLE_TYPES: AccountType[] = ['BANK_CHECKING', 'CASH'];

function statusClasses(status: string): { tile: string; icon: string; iconBg: string } {
  switch (status) {
    case 'red':
      return {
        tile: 'border-rose-400/30 bg-rose-500/10 text-rose-400',
        icon: 'text-rose-400',
        iconBg: 'bg-rose-500/10',
      };
    case 'amber':
      return {
        tile: 'border-amber-400/30 bg-amber-500/10 text-amber-400',
        icon: 'text-amber-400',
        iconBg: 'bg-amber-500/10',
      };
    case 'green':
      return {
        tile: 'border-emerald-400/30 bg-emerald-500/10 text-emerald-400',
        icon: 'text-emerald-400',
        iconBg: 'bg-emerald-500/10',
      };
    default:
      return {
        tile: 'border-border bg-muted/20 text-muted-foreground',
        icon: 'text-muted-foreground',
        iconBg: 'bg-muted/20',
      };
  }
}

function statusIcon(status: string): React.ElementType {
  if (status === 'green') return ShieldCheck;
  if (status === 'red' || status === 'amber') return ShieldAlert;
  return Shield;
}

export function EmergencyFundCard() {
  const { t } = useTranslation();
  const { data, isLoading } = useEmergencyFund();
  const updateTypes = useUpdateEmergencyFundTypes();

  const colors = data ? statusClasses(data.status) : statusClasses('insufficient-data');
  const Icon = data ? statusIcon(data.status) : Shield;

  const months = data?.monthsCovered != null ? Number(data.monthsCovered) : null;
  const reserve = data?.currentReserve != null ? Number(data.currentReserve) : 0;
  const avgExpense =
    data?.monthlyAverageExpense != null ? Number(data.monthlyAverageExpense) : 0;

  const handleToggle = (type: AccountType, currentlyOn: boolean) => {
    if (!data) return;
    const next = currentlyOn
      ? data.includedTypes.filter((tp) => tp !== type)
      : [...data.includedTypes, type];
    // BANK_SAVINGS is always-on; ensure it stays in the list.
    if (!next.includes('BANK_SAVINGS')) {
      next.unshift('BANK_SAVINGS');
    }
    updateTypes.mutate({ types: next });
  };

  return (
    <Card className="overflow-hidden">
      <div className="px-5 py-4 border-b border-border/60 flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div
            className={cn(
              'w-10 h-10 rounded-lg flex items-center justify-center shrink-0',
              colors.iconBg,
            )}
          >
            <Icon className={cn('w-4 h-4', colors.icon)} />
          </div>
          <div className="space-y-0.5">
            <h3 className="text-sm font-medium">{t('emergencyFund.title')}</h3>
            <p className="text-[11px] text-muted-foreground">
              {t('emergencyFund.subtitle')}
            </p>
          </div>
        </div>
      </div>

      <CardContent className="p-5 space-y-5">
        {isLoading && !data ? (
          <div className="h-32 flex items-center justify-center text-xs text-muted-foreground">
            {t('common.loading')}
          </div>
        ) : !data ? (
          <div className="h-32 flex items-center justify-center text-xs text-muted-foreground">
            {t('common.noData')}
          </div>
        ) : (
          <>
            <div
              className={cn(
                'relative rounded-xl border p-5 overflow-hidden',
                colors.tile,
              )}
            >
              <Icon className="absolute -bottom-4 -right-4 w-24 h-24 opacity-10" />
              <p className="text-[10px] uppercase tracking-widest font-medium opacity-80">
                {t('emergencyFund.monthsCoveredLabel')}
              </p>
              {months == null ? (
                <p className="text-3xl font-semibold font-mono tabular-nums tracking-tight mt-2">
                  --
                </p>
              ) : Number(data.monthsCovered) >= 999 ? (
                <p className="text-3xl font-semibold font-mono tabular-nums tracking-tight mt-2">
                  ∞
                </p>
              ) : (
                <p className="text-3xl font-semibold font-mono tabular-nums tracking-tight mt-2">
                  {months.toFixed(1)}
                  <span className="text-base ml-1 opacity-80">
                    {t('emergencyFund.monthsUnit')}
                  </span>
                </p>
              )}
              <p className="text-[11px] mt-1 opacity-80">
                {data.status === 'insufficient-data'
                  ? t('emergencyFund.needMoreData', {
                      count: Math.max(0, 3 - data.sampleMonths),
                    })
                  : t(`emergencyFund.status${capitalize(data.status)}`)}
              </p>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
              <MiniTile
                label={t('emergencyFund.currentReserve')}
                value={formatTRY(reserve)}
                icon={<Wallet className="w-3.5 h-3.5 text-sky-400" />}
              />
              <MiniTile
                label={t('emergencyFund.monthlyAverage')}
                value={formatTRY(avgExpense)}
                icon={<TrendingDown className="w-3.5 h-3.5 text-rose-400" />}
              />
              <MiniTile
                label={t('emergencyFund.includedTypes')}
                value={String(data.includedTypes.length)}
                icon={<Shield className="w-3.5 h-3.5 text-amber-400" />}
              />
            </div>

            <div className="rounded-xl border border-border/60 bg-card/40 p-4 space-y-3">
              <p className="text-[10px] uppercase tracking-widest text-muted-foreground font-medium">
                {t('emergencyFund.includeToggles')}
              </p>
              <div className="flex flex-wrap gap-2">
                <ToggleChip
                  label={t('emergencyFund.toggleSavings')}
                  active
                  locked
                />
                {TOGGLEABLE_TYPES.map((type) => {
                  const active = data.includedTypes.includes(type);
                  return (
                    <ToggleChip
                      key={type}
                      label={t(
                        type === 'BANK_CHECKING'
                          ? 'emergencyFund.toggleChecking'
                          : 'emergencyFund.toggleCash',
                      )}
                      active={active}
                      onClick={() => handleToggle(type, active)}
                      disabled={updateTypes.isPending}
                    />
                  );
                })}
              </div>
            </div>

            <p className="text-[11px] text-muted-foreground text-center">
              {t('emergencyFund.crossCurrencyNote')}
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function MiniTile({
  label,
  value,
  icon,
}: {
  label: string;
  value: string;
  icon: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-border/60 bg-card/50 p-3">
      <div className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-muted-foreground mb-1.5">
        {icon}
        <span>{label}</span>
      </div>
      <p className="text-sm font-mono tabular-nums font-semibold">{value}</p>
    </div>
  );
}

function ToggleChip({
  label,
  active,
  locked,
  disabled,
  onClick,
}: {
  label: string;
  active: boolean;
  locked?: boolean;
  disabled?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={locked || disabled}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors',
        active
          ? 'border-emerald-400/40 bg-emerald-500/10 text-emerald-300'
          : 'border-border text-muted-foreground hover:bg-accent',
        locked
          ? 'cursor-not-allowed opacity-80'
          : disabled
            ? 'cursor-not-allowed opacity-60'
            : 'cursor-pointer',
      )}
    >
      <span
        className={cn(
          'w-1.5 h-1.5 rounded-full',
          active ? 'bg-emerald-400' : 'bg-muted-foreground/40',
        )}
      />
      {label}
    </button>
  );
}
