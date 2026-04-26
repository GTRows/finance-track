import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import {
  CalendarClock,
  Plus,
  Trash2,
  Play,
  Pencil,
  Pause,
  PlayCircle,
  ArrowDownRight,
  ArrowUpRight,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { useCategories } from '@/hooks/useBudget';
import {
  useCreateRecurring,
  useDeleteRecurring,
  useRecurringTemplates,
  useRunRecurring,
  useUpdateRecurring,
} from '@/hooks/useRecurring';
import type { RecurringTemplate, UpsertRecurringRequest } from '@/types/recurring.types';
import type { BudgetTxnType } from '@/types/budget.types';
import type { ApiError } from '@/types/auth.types';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';

type DialogState =
  | { mode: 'closed' }
  | { mode: 'create' }
  | { mode: 'edit'; template: RecurringTemplate };

export function RecurringTemplatesSection() {
  const { t, i18n } = useTranslation();
  const { data: templates = [] } = useRecurringTemplates();
  const { data: categories } = useCategories();
  const createM = useCreateRecurring();
  const updateM = useUpdateRecurring();
  const deleteM = useDeleteRecurring();
  const runM = useRunRecurring();

  const [dialog, setDialog] = useState<DialogState>({ mode: 'closed' });
  const [confirming, setConfirming] = useState<string | null>(null);

  const locale = i18n.resolvedLanguage === 'tr' ? 'tr-TR' : 'en-US';

  const handleDelete = (id: string) => {
    if (confirming === id) {
      deleteM.mutate(id);
      setConfirming(null);
    } else {
      setConfirming(id);
      setTimeout(() => setConfirming(null), 3000);
    }
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0">
        <div>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarClock className="w-4 h-4 text-primary" />
            {t('recurring.title')}
          </CardTitle>
          <p className="text-xs text-muted-foreground mt-1">{t('recurring.description')}</p>
        </div>
        <Button
          size="sm"
          variant="outline"
          onClick={() => setDialog({ mode: 'create' })}
          className="cursor-pointer"
        >
          <Plus className="w-3.5 h-3.5 mr-1.5" />
          {t('recurring.add')}
        </Button>
      </CardHeader>
      <CardContent>
        {templates.length === 0 ? (
          <div className="py-8 text-center text-sm text-muted-foreground">{t('recurring.empty')}</div>
        ) : (
          <div className="divide-y divide-border/40">
            {templates.map((tpl) => (
              <div
                key={tpl.id}
                className={cn(
                  'flex items-center gap-3 py-3 group',
                  !tpl.active && 'opacity-60'
                )}
              >
                <span
                  className={cn(
                    'w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0',
                    tpl.txnType === 'INCOME'
                      ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400'
                      : 'bg-rose-500/15 text-rose-600 dark:text-rose-400'
                  )}
                >
                  {tpl.txnType === 'INCOME' ? (
                    <ArrowDownRight className="w-3.5 h-3.5" />
                  ) : (
                    <ArrowUpRight className="w-3.5 h-3.5" />
                  )}
                </span>

                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">
                    {tpl.description || tpl.categoryName || t('recurring.untitled')}
                  </p>
                  <p className="text-[11px] text-muted-foreground">
                    {t('recurring.dayOfMonthShort', { day: tpl.dayOfMonth })}
                    {tpl.categoryName && tpl.description && (
                      <span className="ml-1.5 opacity-70">{tpl.categoryName}</span>
                    )}
                  </p>
                </div>

                <div className="text-right flex-shrink-0">
                  <p
                    className={cn(
                      'text-sm font-mono tabular-nums font-medium',
                      tpl.txnType === 'INCOME' ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'
                    )}
                  >
                    {tpl.txnType === 'INCOME' ? '+' : '-'}
                    {formatTRY(Number(tpl.amount))}
                  </p>
                  <p className="text-[11px] text-muted-foreground">
                    {tpl.active
                      ? t('recurring.nextOn', {
                          date: new Date(tpl.nextDueOn).toLocaleDateString(locale, {
                            day: 'numeric',
                            month: 'short',
                          }),
                        })
                      : t('recurring.paused')}
                  </p>
                </div>

                <div className="flex items-center gap-0.5 flex-shrink-0">
                  <IconButton
                    title={t('recurring.runNow')}
                    onClick={() => runM.mutate(tpl.id)}
                    disabled={runM.isPending}
                  >
                    <Play className="w-3.5 h-3.5" />
                  </IconButton>
                  <IconButton
                    title={tpl.active ? t('recurring.pause') : t('recurring.resume')}
                    onClick={() =>
                      updateM.mutate({
                        id: tpl.id,
                        req: templateToRequest(tpl, { active: !tpl.active }),
                      })
                    }
                  >
                    {tpl.active ? (
                      <Pause className="w-3.5 h-3.5" />
                    ) : (
                      <PlayCircle className="w-3.5 h-3.5" />
                    )}
                  </IconButton>
                  <IconButton
                    title={t('common.edit')}
                    onClick={() => setDialog({ mode: 'edit', template: tpl })}
                  >
                    <Pencil className="w-3.5 h-3.5" />
                  </IconButton>
                  <IconButton
                    title={confirming === tpl.id ? t('common.confirmAgain') : t('common.delete')}
                    onClick={() => handleDelete(tpl.id)}
                    className={cn(
                      confirming === tpl.id
                        ? 'bg-rose-500/15 text-rose-500'
                        : 'hover:text-rose-500 hover:bg-rose-500/10'
                    )}
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </IconButton>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>

      <RecurringDialog
        state={dialog}
        onClose={() => setDialog({ mode: 'closed' })}
        onSubmit={async (req) => {
          try {
            if (dialog.mode === 'edit') {
              await updateM.mutateAsync({ id: dialog.template.id, req });
            } else {
              await createM.mutateAsync(req);
            }
            setDialog({ mode: 'closed' });
            return null;
          } catch (err) {
            const axiosError = err as AxiosError<ApiError>;
            return axiosError.response?.data?.error || t('recurring.saveError');
          }
        }}
        incomeCategories={categories?.income ?? []}
        expenseCategories={categories?.expense ?? []}
        busy={createM.isPending || updateM.isPending}
      />
    </Card>
  );
}

function templateToRequest(
  tpl: RecurringTemplate,
  overrides: Partial<UpsertRecurringRequest> = {},
): UpsertRecurringRequest {
  return {
    txnType: tpl.txnType,
    amount: tpl.amount,
    categoryId: tpl.categoryId,
    description: tpl.description,
    dayOfMonth: tpl.dayOfMonth,
    active: tpl.active,
    ...overrides,
  };
}

function IconButton({
  children,
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className={cn(
        'w-7 h-7 rounded-md flex items-center justify-center transition-colors cursor-pointer text-muted-foreground hover:text-foreground hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed',
        className,
      )}
    >
      {children}
    </button>
  );
}

interface DialogProps {
  state: DialogState;
  onClose: () => void;
  onSubmit: (req: UpsertRecurringRequest) => Promise<string | null>;
  incomeCategories: Array<{ id: string; name: string }>;
  expenseCategories: Array<{ id: string; name: string }>;
  busy: boolean;
}

function RecurringDialog({
  state,
  onClose,
  onSubmit,
  incomeCategories,
  expenseCategories,
  busy,
}: DialogProps) {
  const { t } = useTranslation();
  const [txnType, setTxnType] = useState<BudgetTxnType>('EXPENSE');
  const [amount, setAmount] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [dayOfMonth, setDayOfMonth] = useState('1');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (state.mode === 'edit') {
      const tpl = state.template;
      setTxnType(tpl.txnType);
      setAmount(String(tpl.amount));
      setCategoryId(tpl.categoryId ?? '');
      setDescription(tpl.description ?? '');
      setDayOfMonth(String(tpl.dayOfMonth));
      setError(null);
    } else if (state.mode === 'create') {
      setTxnType('EXPENSE');
      setAmount('');
      setCategoryId('');
      setDescription('');
      setDayOfMonth('1');
      setError(null);
    }
  }, [state]);

  const open = state.mode !== 'closed';
  const categories = txnType === 'INCOME' ? incomeCategories : expenseCategories;
  const amountNum = Number(amount.replace(',', '.'));
  const dayNum = Number(dayOfMonth);
  const valid =
    Number.isFinite(amountNum) && amountNum > 0 &&
    Number.isFinite(dayNum) && dayNum >= 1 && dayNum <= 31;

  const handleSubmit = async () => {
    if (!valid) return;
    const msg = await onSubmit({
      txnType,
      amount: amountNum,
      categoryId: categoryId || null,
      description: description.trim() || null,
      dayOfMonth: dayNum,
      active: state.mode === 'edit' ? state.template.active : true,
    });
    if (msg) setError(msg);
  };

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
      <DialogContent className="sm:max-w-[460px]">
        <DialogHeader>
          <DialogTitle>
            {state.mode === 'edit' ? t('recurring.editTitle') : t('recurring.addTitle')}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-4 pt-2">
          <div className="grid grid-cols-2 gap-2">
            {(['EXPENSE', 'INCOME'] as const).map((type) => (
              <button
                type="button"
                key={type}
                onClick={() => {
                  setTxnType(type);
                  setCategoryId('');
                }}
                className={cn(
                  'rounded-md border text-sm py-2 transition-colors',
                  txnType === type
                    ? type === 'INCOME'
                      ? 'border-emerald-500/60 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                      : 'border-rose-500/60 bg-rose-500/10 text-rose-600 dark:text-rose-400'
                    : 'border-border text-muted-foreground hover:text-foreground'
                )}
              >
                {type === 'INCOME' ? t('budget.income') : t('budget.expenses')}
              </button>
            ))}
          </div>

          <div className="space-y-1.5">
            <Label>{t('budget.amount')}</Label>
            <div className="relative">
              <Input
                type="text"
                inputMode="decimal"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                className="pr-10 font-mono tabular-nums"
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                TRY
              </span>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('budget.category')}</Label>
            <select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm focus:outline-none focus:ring-1 focus:ring-ring"
            >
              <option value="">{t('budget.uncategorized')}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <Label>{t('budget.description')}</Label>
            <Input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t('budget.descriptionPlaceholder')}
            />
          </div>

          <div className="space-y-1.5">
            <Label>{t('recurring.dayOfMonth')}</Label>
            <Input
              type="number"
              min={1}
              max={31}
              value={dayOfMonth}
              onChange={(e) => setDayOfMonth(e.target.value)}
              className="w-24 font-mono tabular-nums"
            />
            <p className="text-[11px] text-muted-foreground">{t('recurring.dayOfMonthHint')}</p>
          </div>

          {error && <p className="text-xs text-destructive">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSubmit} disabled={!valid || busy}>
            {busy ? t('common.saving') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
