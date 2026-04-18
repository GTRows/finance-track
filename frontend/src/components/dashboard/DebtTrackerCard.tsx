import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  useDebts,
  useCreateDebt,
  useUpdateDebt,
  useArchiveDebt,
  useAddDebtPayment,
} from '@/hooks/useDebts';
import type { Debt, DebtType, UpsertDebtRequest } from '@/types/debt.types';
import { formatTRY, formatShortDate } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import {
  CreditCard,
  Plus,
  Pencil,
  Trash2,
  Building2,
  Car,
  Wallet,
  GraduationCap,
  CircleDollarSign,
  TrendingDown,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

const TYPE_META: Record<DebtType, { icon: typeof CreditCard; tone: string }> = {
  MORTGAGE: { icon: Building2, tone: 'text-amber-400' },
  AUTO: { icon: Car, tone: 'text-sky-400' },
  PERSONAL: { icon: Wallet, tone: 'text-rose-400' },
  CREDIT_CARD: { icon: CreditCard, tone: 'text-rose-400' },
  STUDENT: { icon: GraduationCap, tone: 'text-emerald-400' },
  OTHER: { icon: CircleDollarSign, tone: 'text-muted-foreground' },
};

export function DebtTrackerCard() {
  const { t } = useTranslation();
  const { data, isLoading } = useDebts();
  const [editing, setEditing] = useState<Debt | null>(null);
  const [creating, setCreating] = useState(false);

  const debts = data ?? [];
  const totalRemaining = debts.reduce((sum, d) => sum + d.remainingBalance, 0);
  const totalMonthly = debts.reduce(
    (sum, d) => (d.status === 'ACTIVE' ? sum + d.scheduledMonthlyPayment : sum),
    0
  );

  return (
    <Card className="overflow-hidden">
      <div className="px-5 py-4 border-b border-border/60 flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-lg bg-rose-500/10 flex items-center justify-center shrink-0">
            <TrendingDown className="w-4 h-4 text-rose-400" />
          </div>
          <div className="space-y-0.5">
            <h3 className="text-sm font-medium">{t('debt.title')}</h3>
            <p className="text-[11px] text-muted-foreground">{t('debt.subtitle')}</p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          {debts.length > 0 && (
            <div className="text-right">
              <p className="text-lg font-semibold font-mono tabular-nums tracking-tight text-rose-400">
                {formatTRY(totalRemaining)}
              </p>
              <p className="text-[11px] text-muted-foreground font-mono tabular-nums">
                {formatTRY(totalMonthly)} {t('debt.perMonth')}
              </p>
            </div>
          )}
          <DebtDialog
            open={creating}
            onOpenChange={setCreating}
            trigger={
              <Button size="sm" variant="outline" className="h-8 cursor-pointer">
                <Plus className="w-3.5 h-3.5 mr-1" />
                {t('debt.newDebt')}
              </Button>
            }
          />
        </div>
      </div>

      <CardContent className="p-0">
        {isLoading && debts.length === 0 ? (
          <div className="h-32 flex items-center justify-center text-xs text-muted-foreground">
            {t('common.loading')}
          </div>
        ) : debts.length === 0 ? (
          <div className="h-40 flex flex-col items-center justify-center gap-2 text-center px-6">
            <div className="w-10 h-10 rounded-xl bg-muted flex items-center justify-center">
              <CreditCard className="w-5 h-5 text-muted-foreground" />
            </div>
            <p className="text-xs text-muted-foreground max-w-[280px]">
              {t('debt.empty')}
            </p>
          </div>
        ) : (
          <ul className="divide-y divide-border/60">
            {debts.map((debt) => (
              <DebtRow key={debt.id} debt={debt} onEdit={() => setEditing(debt)} />
            ))}
          </ul>
        )}

        {editing && (
          <DebtDialog
            debt={editing}
            open={!!editing}
            onOpenChange={(v) => {
              if (!v) setEditing(null);
            }}
          />
        )}
      </CardContent>
    </Card>
  );
}

function DebtRow({ debt, onEdit }: { debt: Debt; onEdit: () => void }) {
  const { t } = useTranslation();
  const archive = useArchiveDebt();
  const [paying, setPaying] = useState(false);
  const [showSchedule, setShowSchedule] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const meta = TYPE_META[debt.debtType] ?? TYPE_META.OTHER;
  const Icon = meta.icon;
  const paid = debt.status === 'PAID_OFF';
  const ratio = Math.min(1, Math.max(0, debt.progressRatio));

  const aheadLabel =
    debt.monthsAhead > 0
      ? t('debt.aheadMonths', { count: debt.monthsAhead })
      : debt.monthsAhead < 0
        ? t('debt.behindMonths', { count: -debt.monthsAhead })
        : null;

  const handleArchive = () => {
    if (confirming) {
      archive.mutate(debt.id);
      setConfirming(false);
    } else {
      setConfirming(true);
      setTimeout(() => setConfirming(false), 3000);
    }
  };

  return (
    <li className="px-5 py-4 group hover:bg-accent/20 transition-colors">
      <div className="flex items-start gap-4">
        <div className={cn('w-10 h-10 rounded-lg bg-card border border-border/60 flex items-center justify-center shrink-0')}>
          <Icon className={cn('w-4 h-4', meta.tone)} />
        </div>

        <div className="flex-1 min-w-0 space-y-2">
          <div className="flex items-baseline justify-between gap-3">
            <div className="min-w-0">
              <p className="text-sm font-medium truncate">{debt.name}</p>
              <p className="text-[11px] text-muted-foreground">
                {t(`debt.type.${debt.debtType}`)}
                {' -- '}
                {(debt.annualRate * 100).toFixed(2)}% {t('debt.apr')}
                {' -- '}
                {debt.termMonths} {t('debt.months')}
              </p>
            </div>
            <div className="text-right shrink-0">
              <p className="text-sm font-mono tabular-nums font-semibold">
                {formatTRY(debt.remainingBalance)}
              </p>
              <p className="text-[11px] text-muted-foreground font-mono tabular-nums">
                / {formatTRY(debt.principal)}
              </p>
            </div>
          </div>

          <div className="space-y-1">
            <div className="h-1.5 w-full rounded-full bg-border/50 overflow-hidden">
              <div
                className={cn(
                  'h-full rounded-full transition-all duration-500',
                  paid ? 'bg-emerald-400' : 'bg-gradient-to-r from-rose-400 to-amber-400'
                )}
                style={{ width: `${Math.round(ratio * 100)}%` }}
              />
            </div>
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-muted-foreground">
                {Math.round(ratio * 100)}% {t('debt.paidOff')}
              </span>
              {paid ? (
                <span className="text-emerald-400 font-medium">{t('debt.statusPaidOff')}</span>
              ) : (
                <div className="flex items-center gap-3">
                  {aheadLabel && (
                    <span
                      className={cn(
                        'font-medium',
                        debt.monthsAhead > 0 ? 'text-emerald-400' : 'text-amber-400'
                      )}
                    >
                      {aheadLabel}
                    </span>
                  )}
                  {debt.projectedPayoffDate && (
                    <span className="text-muted-foreground">
                      {t('debt.payoff', { date: formatShortDate(debt.projectedPayoffDate) })}
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center justify-between text-[11px] text-muted-foreground">
            <span className="font-mono tabular-nums">
              {t('debt.monthly')}{' '}
              <span className="text-foreground font-semibold">
                {formatTRY(debt.scheduledMonthlyPayment)}
              </span>
            </span>
            {!paid && debt.nextPayments.length > 0 && (
              <button
                onClick={() => setShowSchedule((v) => !v)}
                className="inline-flex items-center gap-1 hover:text-foreground transition-colors cursor-pointer"
              >
                {showSchedule ? t('debt.hideSchedule') : t('debt.showSchedule')}
                {showSchedule ? (
                  <ChevronUp className="w-3 h-3" />
                ) : (
                  <ChevronDown className="w-3 h-3" />
                )}
              </button>
            )}
          </div>

          {showSchedule && debt.nextPayments.length > 0 && (
            <div className="mt-2 rounded-md border border-border/50 overflow-hidden">
              <table className="w-full text-[11px]">
                <thead className="bg-muted/40">
                  <tr className="text-left text-muted-foreground">
                    <th className="px-2 py-1.5 font-normal">{t('debt.col.due')}</th>
                    <th className="px-2 py-1.5 font-normal text-right">{t('debt.col.payment')}</th>
                    <th className="px-2 py-1.5 font-normal text-right">{t('debt.col.principal')}</th>
                    <th className="px-2 py-1.5 font-normal text-right">{t('debt.col.interest')}</th>
                    <th className="px-2 py-1.5 font-normal text-right">{t('debt.col.balance')}</th>
                  </tr>
                </thead>
                <tbody className="font-mono tabular-nums">
                  {debt.nextPayments.map((row, i) => (
                    <tr key={i} className="border-t border-border/40">
                      <td className="px-2 py-1.5">{formatShortDate(row.dueDate)}</td>
                      <td className="px-2 py-1.5 text-right">{formatTRY(row.payment)}</td>
                      <td className="px-2 py-1.5 text-right text-emerald-400">
                        {formatTRY(row.principal)}
                      </td>
                      <td className="px-2 py-1.5 text-right text-rose-400">
                        {formatTRY(row.interest)}
                      </td>
                      <td className="px-2 py-1.5 text-right">{formatTRY(row.remainingBalance)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {paying && <PaymentInline debtId={debt.id} onClose={() => setPaying(false)} />}
        </div>

        <div className="opacity-0 group-hover:opacity-100 flex items-center gap-1 transition-opacity shrink-0">
          {!paid && (
            <button
              onClick={() => setPaying((v) => !v)}
              className="p-1 rounded hover:bg-accent cursor-pointer"
              title={t('debt.recordPayment')}
            >
              <Plus className="w-3.5 h-3.5 text-emerald-400" />
            </button>
          )}
          <button
            onClick={onEdit}
            className="p-1 rounded hover:bg-accent cursor-pointer"
            title={t('common.edit')}
          >
            <Pencil className="w-3 h-3 text-muted-foreground" />
          </button>
          <button
            onClick={handleArchive}
            className={cn(
              'p-1 rounded cursor-pointer',
              confirming ? 'bg-destructive/20 text-destructive' : 'hover:bg-destructive/10 text-muted-foreground'
            )}
            title={confirming ? t('common.confirmAgain') : t('debt.archive')}
          >
            <Trash2 className="w-3 h-3" />
          </button>
        </div>
      </div>
    </li>
  );
}

function PaymentInline({ debtId, onClose }: { debtId: string; onClose: () => void }) {
  const { t } = useTranslation();
  const add = useAddDebtPayment();
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [note, setNote] = useState('');

  const submit = async () => {
    const value = Number(amount.replace(',', '.'));
    if (!value || value <= 0) return;
    await add.mutateAsync({
      id: debtId,
      req: { paymentDate: date, amount: value, note: note.trim() || undefined },
    });
    setAmount('');
    setNote('');
    onClose();
  };

  return (
    <div className="mt-2 flex items-center gap-2 rounded-md bg-emerald-500/5 ring-1 ring-emerald-400/20 px-2 py-2">
      <Input
        type="number"
        step="0.01"
        placeholder={t('debt.amountPlaceholder')}
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        className="h-7 text-xs font-mono tabular-nums w-32"
        autoFocus
      />
      <Input
        type="date"
        value={date}
        onChange={(e) => setDate(e.target.value)}
        className="h-7 text-xs w-36"
      />
      <Input
        placeholder={t('debt.notePlaceholder')}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        className="h-7 text-xs flex-1"
      />
      <Button
        size="sm"
        variant="ghost"
        onClick={onClose}
        className="h-7 px-2 text-[11px] text-muted-foreground"
      >
        {t('common.cancel')}
      </Button>
      <Button
        size="sm"
        onClick={submit}
        disabled={add.isPending || !amount}
        className="h-7 px-3 text-[11px] cursor-pointer"
      >
        {add.isPending ? t('common.saving') : t('debt.recordPayment')}
      </Button>
    </div>
  );
}

interface DebtDialogProps {
  debt?: Debt;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  trigger?: React.ReactNode;
}

const TYPE_OPTIONS: DebtType[] = [
  'MORTGAGE',
  'AUTO',
  'PERSONAL',
  'CREDIT_CARD',
  'STUDENT',
  'OTHER',
];

function DebtDialog({ debt, open, onOpenChange, trigger }: DebtDialogProps) {
  const { t } = useTranslation();
  const create = useCreateDebt();
  const update = useUpdateDebt();
  const isEdit = !!debt;

  const [name, setName] = useState(debt?.name ?? '');
  const [type, setType] = useState<DebtType>(debt?.debtType ?? 'MORTGAGE');
  const [principal, setPrincipal] = useState(
    debt?.principal ? String(debt.principal) : ''
  );
  const [aprPercent, setAprPercent] = useState(
    debt?.annualRate != null ? (debt.annualRate * 100).toFixed(2) : ''
  );
  const [term, setTerm] = useState(debt?.termMonths ? String(debt.termMonths) : '');
  const [start, setStart] = useState(
    debt?.startDate ?? new Date().toISOString().slice(0, 10)
  );
  const [notes, setNotes] = useState(debt?.notes ?? '');

  const submit = async () => {
    const principalValue = Number(principal.replace(',', '.'));
    const rateValue = Number(aprPercent.replace(',', '.')) / 100;
    const termValue = Number(term);
    if (!name.trim() || !principalValue || !termValue) return;

    const req: UpsertDebtRequest = {
      name: name.trim(),
      debtType: type,
      principal: principalValue,
      annualRate: rateValue,
      termMonths: termValue,
      startDate: start,
      notes: notes.trim() || null,
    };

    try {
      if (isEdit && debt) {
        await update.mutateAsync({ id: debt.id, req });
      } else {
        await create.mutateAsync(req);
        setName('');
        setPrincipal('');
        setAprPercent('');
        setTerm('');
        setNotes('');
      }
      onOpenChange(false);
    } catch {
      // surfaced via toast elsewhere
    }
  };

  const pending = create.isPending || update.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {trigger && <DialogTrigger asChild>{trigger}</DialogTrigger>}
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>{isEdit ? t('debt.editDebt') : t('debt.newDebt')}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 pt-1">
          <div className="space-y-1.5">
            <Label>{t('debt.name')}</Label>
            <Input
              value={name}
              placeholder={t('debt.namePlaceholder')}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <Label>{t('debt.debtType')}</Label>
            <div className="grid grid-cols-3 gap-1.5">
              {TYPE_OPTIONS.map((k) => {
                const meta = TYPE_META[k];
                const Icon = meta.icon;
                const active = type === k;
                return (
                  <button
                    key={k}
                    type="button"
                    onClick={() => setType(k)}
                    className={cn(
                      'flex items-center gap-2 rounded-lg border px-2.5 py-2 text-[11px] cursor-pointer transition-colors',
                      active
                        ? 'border-primary bg-primary/5'
                        : 'border-border/60 hover:border-border'
                    )}
                  >
                    <Icon className={cn('w-3.5 h-3.5', meta.tone)} />
                    <span>{t(`debt.type.${k}`)}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label>{t('debt.principal')}</Label>
              <div className="relative">
                <Input
                  type="number"
                  step="0.01"
                  value={principal}
                  onChange={(e) => setPrincipal(e.target.value)}
                  placeholder="0"
                  className="pr-10 font-mono tabular-nums"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground">
                  TRY
                </span>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>{t('debt.apr')}</Label>
              <div className="relative">
                <Input
                  type="number"
                  step="0.01"
                  value={aprPercent}
                  onChange={(e) => setAprPercent(e.target.value)}
                  placeholder="0"
                  className="pr-7 font-mono tabular-nums"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground">
                  %
                </span>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>{t('debt.term')}</Label>
              <div className="relative">
                <Input
                  type="number"
                  value={term}
                  onChange={(e) => setTerm(e.target.value)}
                  placeholder="0"
                  className="pr-9 font-mono tabular-nums"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground">
                  mo
                </span>
              </div>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('debt.startDate')}</Label>
            <Input type="date" value={start} onChange={(e) => setStart(e.target.value)} />
          </div>

          <div className="space-y-1.5">
            <Label>{t('debt.notes')}</Label>
            <Textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={t('debt.notesPlaceholder')}
              rows={2}
            />
          </div>

          <Button
            onClick={submit}
            disabled={pending || !name.trim() || !principal || !term}
            className="w-full cursor-pointer"
          >
            {pending ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
