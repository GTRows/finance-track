import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useSubscriptionAudit, useMarkBillUsed, useDeleteBill } from '@/hooks/useBills';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import { Sparkles, CircleSlash, Scan, HandCoins } from 'lucide-react';
import { useState } from 'react';
import type { SubscriptionAuditCandidate } from '@/types/bill.types';

export function SubscriptionAuditCard() {
  const { t } = useTranslation();
  const auditQuery = useSubscriptionAudit();
  const markUsed = useMarkBillUsed();
  const deleteBill = useDeleteBill();
  const [confirmingCancel, setConfirmingCancel] = useState<string | null>(null);

  const audit = auditQuery.data;
  if (!audit) return null;

  const hasCandidates = audit.candidates.length > 0;

  const handleCancel = (id: string) => {
    if (confirmingCancel === id) {
      deleteBill.mutate(id);
      setConfirmingCancel(null);
    } else {
      setConfirmingCancel(id);
      setTimeout(() => setConfirmingCancel(null), 3000);
    }
  };

  return (
    <Card className={cn('overflow-hidden', hasCandidates && 'border-amber-500/30')}>
      <div
        className={cn(
          'px-5 py-4 border-b border-border/60 flex items-start justify-between gap-4',
          hasCandidates
            ? 'bg-gradient-to-r from-amber-500/[0.06] via-transparent to-transparent'
            : 'bg-gradient-to-r from-emerald-500/[0.05] via-transparent to-transparent'
        )}
      >
        <div className="flex items-start gap-3">
          <div
            className={cn(
              'w-9 h-9 rounded-lg flex items-center justify-center shrink-0',
              hasCandidates ? 'bg-amber-500/15 text-amber-400' : 'bg-emerald-500/15 text-emerald-400'
            )}
          >
            <Scan className="w-4 h-4" strokeWidth={1.75} />
          </div>
          <div className="space-y-0.5">
            <h3 className="text-sm font-medium">{t('bills.audit.title')}</h3>
            <p className="text-[11px] text-muted-foreground">{t('bills.audit.subtitle')}</p>
          </div>
        </div>

        {hasCandidates && (
          <div className="text-right shrink-0">
            <p className="text-[10px] text-muted-foreground uppercase tracking-widest">
              {t('bills.audit.potentialSavings')}
            </p>
            <p className="text-xl font-semibold font-mono tabular-nums tracking-tight text-amber-400">
              {formatTRY(audit.potentialMonthlySavings)}
            </p>
            <p className="text-[10px] text-muted-foreground">
              {t('bills.audit.perMonth')} ·{' '}
              {t('bills.audit.flagged', { count: audit.candidateCount })}
            </p>
          </div>
        )}
      </div>

      <CardContent className="p-0">
        {hasCandidates ? (
          <ul className="divide-y divide-border/60">
            {audit.candidates.map((c) => (
              <li key={c.billId} className="px-5 py-3 flex items-center gap-3 group">
                <ReasonDot reason={c.reason} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium truncate">{c.name}</p>
                    {c.category && (
                      <span className="text-[10px] text-muted-foreground uppercase tracking-wider">
                        · {c.category}
                      </span>
                    )}
                  </div>
                  <p className="text-[11px] text-muted-foreground">
                    {c.reason === 'NEVER_USED'
                      ? t('bills.audit.reasonNeverUsed')
                      : t('bills.audit.reasonStale', { days: c.daysSinceLastUse ?? 0 })}
                  </p>
                </div>

                <span className="text-sm font-mono tabular-nums font-medium shrink-0">
                  {formatTRY(c.amount)}
                </span>

                <div className="flex items-center gap-1.5 shrink-0">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-[11px] cursor-pointer text-emerald-400 hover:text-emerald-300 hover:bg-emerald-500/10"
                    onClick={() => markUsed.mutate(c.billId)}
                    disabled={markUsed.isPending}
                    title={t('bills.audit.stillUsingIt')}
                  >
                    <HandCoins className="w-3.5 h-3.5 mr-1" />
                    {t('bills.audit.stillUsingIt')}
                  </Button>
                  <button
                    onClick={() => handleCancel(c.billId)}
                    className={cn(
                      'h-7 px-2 text-[11px] rounded-md inline-flex items-center gap-1 cursor-pointer transition-colors',
                      confirmingCancel === c.billId
                        ? 'bg-destructive/20 text-destructive'
                        : 'text-muted-foreground hover:bg-destructive/10 hover:text-destructive'
                    )}
                    title={t('bills.audit.cancelIt')}
                  >
                    <CircleSlash className="w-3.5 h-3.5" />
                    {t('bills.audit.cancelIt')}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <div className="px-5 py-8 flex items-center justify-center gap-2 text-xs text-muted-foreground">
            <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
            {t('bills.audit.empty')}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function ReasonDot({ reason }: { reason: SubscriptionAuditCandidate['reason'] }) {
  return (
    <span
      className={cn(
        'w-1.5 h-1.5 rounded-full shrink-0',
        reason === 'NEVER_USED' ? 'bg-rose-400' : 'bg-amber-400'
      )}
    />
  );
}
