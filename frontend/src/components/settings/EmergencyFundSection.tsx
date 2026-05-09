import { useTranslation } from 'react-i18next';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import {
  useEmergencyFund,
  useUpdateEmergencyFundConfig,
} from '@/hooks/useEmergencyFund';
import type { AccountType } from '@/types/account.types';
import { cn } from '@/lib/utils';

const TOGGLEABLE_TYPES: AccountType[] = ['BANK_CHECKING', 'CASH'];
const MIN_TARGET = 2;
const MAX_TARGET = 24;
const MIN_AMBER_FLOOR = 1;

export function EmergencyFundSection() {
  const { t } = useTranslation();
  const { data, isLoading } = useEmergencyFund();
  const updateConfig = useUpdateEmergencyFundConfig();

  if (isLoading || !data) {
    return <p className="text-xs text-muted-foreground">{t('common.loading')}</p>;
  }

  const handleTargetChange = (next: number) => {
    if (Number.isNaN(next)) return;
    const safeNext = Math.min(MAX_TARGET, Math.max(MIN_TARGET, next));
    const safeAmberFloor = Math.min(data.amberFloorMonths, safeNext - 1);
    updateConfig.mutate({
      types: data.includedTypes,
      targetMonths: safeNext,
      amberFloorMonths: safeAmberFloor,
    });
  };

  const handleAmberFloorChange = (next: number) => {
    if (Number.isNaN(next)) return;
    const upperBound = Math.max(MIN_AMBER_FLOOR, data.targetMonths - 1);
    const safeNext = Math.min(upperBound, Math.max(MIN_AMBER_FLOOR, next));
    updateConfig.mutate({
      types: data.includedTypes,
      targetMonths: data.targetMonths,
      amberFloorMonths: safeNext,
    });
  };

  const handleToggle = (type: AccountType, currentlyOn: boolean) => {
    const next = currentlyOn
      ? data.includedTypes.filter((tp) => tp !== type)
      : [...data.includedTypes, type];
    if (!next.includes('BANK_SAVINGS')) next.unshift('BANK_SAVINGS');
    updateConfig.mutate({
      types: next,
      targetMonths: data.targetMonths,
      amberFloorMonths: data.amberFloorMonths,
    });
  };

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="ef-target">{t('emergencyFund.targetMonths')}</Label>
          <Input
            id="ef-target"
            type="number"
            min={MIN_TARGET}
            max={MAX_TARGET}
            value={data.targetMonths}
            disabled={updateConfig.isPending}
            onChange={(e) => handleTargetChange(parseInt(e.target.value || '0', 10))}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="ef-amber">{t('emergencyFund.amberFloorMonths')}</Label>
          <Input
            id="ef-amber"
            type="number"
            min={MIN_AMBER_FLOOR}
            max={Math.max(MIN_AMBER_FLOOR, data.targetMonths - 1)}
            value={data.amberFloorMonths}
            disabled={updateConfig.isPending}
            onChange={(e) => handleAmberFloorChange(parseInt(e.target.value || '0', 10))}
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label>{t('emergencyFund.includeToggles')}</Label>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled
            className="inline-flex items-center gap-1.5 rounded-full border border-emerald-400/40 bg-emerald-500/10 text-emerald-300 px-3 py-1 text-xs font-medium opacity-80 cursor-not-allowed"
          >
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            {t('emergencyFund.toggleSavings')}
          </button>
          {TOGGLEABLE_TYPES.map((type) => {
            const active = data.includedTypes.includes(type);
            return (
              <button
                key={type}
                type="button"
                onClick={() => handleToggle(type, active)}
                disabled={updateConfig.isPending}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors',
                  active
                    ? 'border-emerald-400/40 bg-emerald-500/10 text-emerald-300'
                    : 'border-border text-muted-foreground hover:bg-accent',
                  updateConfig.isPending ? 'cursor-not-allowed opacity-60' : 'cursor-pointer',
                )}
              >
                <span
                  className={cn(
                    'w-1.5 h-1.5 rounded-full',
                    active ? 'bg-emerald-400' : 'bg-muted-foreground/40',
                  )}
                />
                {t(
                  type === 'BANK_CHECKING'
                    ? 'emergencyFund.toggleChecking'
                    : 'emergencyFund.toggleCash',
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
