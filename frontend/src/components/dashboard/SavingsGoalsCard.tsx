import { useMemo, useState } from 'react';
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
  useSavingsGoals,
  useCreateSavingsGoal,
  useUpdateSavingsGoal,
  useArchiveSavingsGoal,
  useAddSavingsContribution,
} from '@/hooks/useSavingsGoals';
import { usePortfolios } from '@/hooks/usePortfolios';
import type {
  SavingsGoal,
  UpsertSavingsGoalRequest,
} from '@/types/savings.types';
import { formatTRY, formatShortDate } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import {
  PiggyBank,
  Plus,
  Pencil,
  Trash2,
  Target,
  TrendingUp,
  CalendarClock,
  Sparkles,
  Link2,
} from 'lucide-react';

export function SavingsGoalsCard() {
  const { t } = useTranslation();
  const { data, isLoading } = useSavingsGoals();
  const [editing, setEditing] = useState<SavingsGoal | null>(null);
  const [creating, setCreating] = useState(false);

  const goals = data ?? [];

  return (
    <Card className="overflow-hidden">
      <div className="px-5 py-4 border-b border-border/60 flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
            <PiggyBank className="w-4 h-4 text-primary" />
          </div>
          <div className="space-y-0.5">
            <h3 className="text-sm font-medium">{t('savings.title')}</h3>
            <p className="text-[11px] text-muted-foreground">{t('savings.subtitle')}</p>
          </div>
        </div>

        <GoalDialog
          open={creating}
          onOpenChange={setCreating}
          trigger={
            <Button size="sm" variant="outline" className="h-8 cursor-pointer">
              <Plus className="w-3.5 h-3.5 mr-1" />
              {t('savings.newGoal')}
            </Button>
          }
        />
      </div>

      <CardContent className="p-0">
        {isLoading && goals.length === 0 ? (
          <div className="h-32 flex items-center justify-center text-xs text-muted-foreground">
            {t('common.loading')}
          </div>
        ) : goals.length === 0 ? (
          <div className="h-40 flex flex-col items-center justify-center gap-2 text-center px-6">
            <div className="w-10 h-10 rounded-xl bg-muted flex items-center justify-center">
              <Target className="w-5 h-5 text-muted-foreground" />
            </div>
            <p className="text-xs text-muted-foreground max-w-[260px]">
              {t('savings.empty')}
            </p>
          </div>
        ) : (
          <ul className="divide-y divide-border/60">
            {goals.map((goal) => (
              <GoalRow
                key={goal.id}
                goal={goal}
                onEdit={() => setEditing(goal)}
              />
            ))}
          </ul>
        )}

        {editing && (
          <GoalDialog
            goal={editing}
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

interface GoalRowProps {
  goal: SavingsGoal;
  onEdit: () => void;
}

function GoalRow({ goal, onEdit }: GoalRowProps) {
  const { t } = useTranslation();
  const archive = useArchiveSavingsGoal();
  const [contributing, setContributing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const ratio = Math.min(1, Math.max(0, goal.progressRatio));
  const reached = goal.status === 'REACHED';
  const remaining = Math.max(0, goal.targetAmount - goal.currentAmount);

  const pace = computePaceState(goal);

  const arc = useMemo(() => {
    const radius = 26;
    const circumference = 2 * Math.PI * radius;
    return {
      radius,
      circumference,
      offset: circumference * (1 - ratio),
    };
  }, [ratio]);

  const arcColor = reached
    ? 'stroke-emerald-400'
    : pace.kind === 'late'
      ? 'stroke-rose-400'
      : pace.kind === 'ontrack'
        ? 'stroke-emerald-400'
        : pace.kind === 'tight'
          ? 'stroke-amber-400'
          : 'stroke-sky-400';

  const handleArchive = () => {
    if (confirming) {
      archive.mutate(goal.id);
      setConfirming(false);
    } else {
      setConfirming(true);
      setTimeout(() => setConfirming(false), 3000);
    }
  };

  return (
    <li className="px-5 py-4 flex items-start gap-4 group hover:bg-accent/20 transition-colors">
      <div className="relative shrink-0">
        <svg width="64" height="64" viewBox="0 0 64 64" className="-rotate-90">
          <circle
            cx="32"
            cy="32"
            r={arc.radius}
            fill="none"
            stroke="hsl(var(--border))"
            strokeWidth="3"
          />
          <circle
            cx="32"
            cy="32"
            r={arc.radius}
            fill="none"
            strokeWidth="3"
            strokeLinecap="round"
            strokeDasharray={arc.circumference}
            strokeDashoffset={arc.offset}
            className={cn('transition-all duration-500', arcColor)}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-[11px] font-mono tabular-nums font-semibold leading-none">
            {Math.round(ratio * 100)}%
          </span>
          {reached && <Sparkles className="w-2.5 h-2.5 text-emerald-400 mt-0.5" />}
        </div>
      </div>

      <div className="flex-1 min-w-0 space-y-1">
        <div className="flex items-baseline justify-between gap-3">
          <p className="text-sm font-medium truncate">{goal.name}</p>
          <span className="text-xs font-mono tabular-nums text-muted-foreground shrink-0">
            <span className="text-foreground font-semibold">{formatTRY(goal.currentAmount)}</span>
            <span className="opacity-60"> / {formatTRY(goal.targetAmount)}</span>
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
          {goal.linkedPortfolioName && (
            <span className="inline-flex items-center gap-1 text-muted-foreground">
              <Link2 className="w-3 h-3" />
              {goal.linkedPortfolioName}
            </span>
          )}
          {!reached && pace.label && (
            <span
              className={cn(
                'inline-flex items-center gap-1 font-medium',
                pace.kind === 'late' && 'text-rose-400',
                pace.kind === 'ontrack' && 'text-emerald-400',
                pace.kind === 'tight' && 'text-amber-400',
                pace.kind === 'unknown' && 'text-muted-foreground'
              )}
            >
              {pace.kind === 'unknown' ? (
                <CalendarClock className="w-3 h-3" />
              ) : (
                <TrendingUp className="w-3 h-3" />
              )}
              {pace.label}
            </span>
          )}
          {goal.targetDate && !reached && (
            <span className="inline-flex items-center gap-1 text-muted-foreground">
              <Target className="w-3 h-3" />
              {t('savings.targetBy', { date: formatShortDate(goal.targetDate) })}
            </span>
          )}
          {reached && (
            <span className="inline-flex items-center gap-1 text-emerald-400 font-medium">
              <Sparkles className="w-3 h-3" />
              {t('savings.reached')}
            </span>
          )}
          {!reached && remaining > 0 && (
            <span className="text-muted-foreground">
              {t('savings.remaining', { amount: formatTRY(remaining) })}
            </span>
          )}
        </div>

        {contributing && (
          <ContributionInline
            goalId={goal.id}
            onClose={() => setContributing(false)}
          />
        )}
      </div>

      <div className="opacity-0 group-hover:opacity-100 flex items-center gap-1 transition-opacity shrink-0">
        {!reached && (
          <button
            onClick={() => setContributing((v) => !v)}
            className="p-1 rounded hover:bg-accent cursor-pointer"
            title={t('savings.contribute')}
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
          title={confirming ? t('common.confirmAgain') : t('savings.archive')}
        >
          <Trash2 className="w-3 h-3" />
        </button>
      </div>
    </li>
  );
}

interface PaceState {
  kind: 'ontrack' | 'tight' | 'late' | 'unknown';
  label: string | null;
}

function computePaceState(goal: SavingsGoal): PaceState {
  if (goal.status === 'REACHED') {
    return { kind: 'ontrack', label: null };
  }
  if (goal.monthlyPace == null || goal.monthlyPace <= 0) {
    return { kind: 'unknown', label: 'savings.paceUnknown' };
  }
  if (!goal.projectedCompletion) {
    return { kind: 'unknown', label: null };
  }

  const projected = new Date(goal.projectedCompletion);
  if (!goal.targetDate) {
    return { kind: 'ontrack', label: paceLabel(projected) };
  }

  const target = new Date(goal.targetDate);
  const diffDays = Math.round((projected.getTime() - target.getTime()) / 86_400_000);

  if (diffDays <= 0) return { kind: 'ontrack', label: paceLabel(projected) };
  if (diffDays <= 30) return { kind: 'tight', label: paceLabel(projected) };
  return { kind: 'late', label: paceLabel(projected) };
}

function paceLabel(date: Date): string {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    year: 'numeric',
  }).format(date);
}

function ContributionInline({ goalId, onClose }: { goalId: string; onClose: () => void }) {
  const { t } = useTranslation();
  const add = useAddSavingsContribution();
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [note, setNote] = useState('');

  const submit = async () => {
    const value = Number(amount.replace(',', '.'));
    if (!value || value <= 0) return;
    await add.mutateAsync({
      id: goalId,
      req: {
        contributionDate: date,
        amount: value,
        note: note.trim() || undefined,
      },
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
        placeholder={t('savings.amountPlaceholder')}
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        className="h-7 text-xs font-mono tabular-nums w-28"
        autoFocus
      />
      <Input
        type="date"
        value={date}
        onChange={(e) => setDate(e.target.value)}
        className="h-7 text-xs w-36"
      />
      <Input
        placeholder={t('savings.notePlaceholder')}
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
        {add.isPending ? t('common.saving') : t('savings.contribute')}
      </Button>
    </div>
  );
}

interface GoalDialogProps {
  goal?: SavingsGoal;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  trigger?: React.ReactNode;
}

function GoalDialog({ goal, open, onOpenChange, trigger }: GoalDialogProps) {
  const { t } = useTranslation();
  const create = useCreateSavingsGoal();
  const update = useUpdateSavingsGoal();
  const portfolios = usePortfolios();

  const isEdit = !!goal;
  const [name, setName] = useState(goal?.name ?? '');
  const [target, setTarget] = useState(goal?.targetAmount ? String(goal.targetAmount) : '');
  const [date, setDate] = useState(goal?.targetDate ?? '');
  const [linkedId, setLinkedId] = useState(goal?.linkedPortfolioId ?? '');
  const [notes, setNotes] = useState(goal?.notes ?? '');

  const submit = async () => {
    const targetValue = Number(target.replace(',', '.'));
    if (!name.trim() || !targetValue || targetValue <= 0) return;

    const req: UpsertSavingsGoalRequest = {
      name: name.trim(),
      targetAmount: targetValue,
      targetDate: date || null,
      linkedPortfolioId: linkedId || null,
      notes: notes.trim() || null,
    };

    try {
      if (isEdit && goal) {
        await update.mutateAsync({ id: goal.id, req });
      } else {
        await create.mutateAsync(req);
        setName('');
        setTarget('');
        setDate('');
        setLinkedId('');
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
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>{isEdit ? t('savings.editGoal') : t('savings.newGoal')}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 pt-1">
          <div className="space-y-1.5">
            <Label>{t('savings.name')}</Label>
            <Input
              value={name}
              placeholder={t('savings.namePlaceholder')}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('savings.targetAmount')}</Label>
              <div className="relative">
                <Input
                  type="number"
                  step="0.01"
                  value={target}
                  onChange={(e) => setTarget(e.target.value)}
                  placeholder="0"
                  className="pr-10 font-mono tabular-nums"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground">
                  TRY
                </span>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>{t('savings.targetDate')}</Label>
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('savings.linkedPortfolio')}</Label>
            <select
              value={linkedId}
              onChange={(e) => setLinkedId(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-background px-2 text-sm"
            >
              <option value="">{t('savings.linkedPortfolioNone')}</option>
              {(portfolios.data ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
            <p className="text-[10px] text-muted-foreground">{t('savings.linkedPortfolioHint')}</p>
          </div>

          <div className="space-y-1.5">
            <Label>{t('savings.notes')}</Label>
            <Textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={t('savings.notesPlaceholder')}
              rows={2}
            />
          </div>

          <Button
            onClick={submit}
            disabled={pending || !name.trim() || !target}
            className="w-full cursor-pointer"
          >
            {pending ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
