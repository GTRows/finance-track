import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Settings2, RotateCcw, Loader2, Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { Category } from '@/types/budget.types';
import { useUpdateExpenseCategory } from '@/hooks/useBudget';

interface BudgetCategoriesDialogProps {
  month: string;
  expenseCategories: Category[];
}

interface RowDraft {
  budgetAmount: string;
  rolloverEnabled: boolean;
}

export function BudgetCategoriesDialog({ month, expenseCategories }: BudgetCategoriesDialogProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const updateCat = useUpdateExpenseCategory(month);

  const initialDrafts = useMemo<Record<string, RowDraft>>(() => {
    const map: Record<string, RowDraft> = {};
    for (const c of expenseCategories) {
      map[c.id] = {
        budgetAmount: c.budgetAmount != null ? String(c.budgetAmount) : '',
        rolloverEnabled: c.rolloverEnabled,
      };
    }
    return map;
  }, [expenseCategories]);

  const [drafts, setDrafts] = useState<Record<string, RowDraft>>(initialDrafts);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [savedId, setSavedId] = useState<string | null>(null);

  useEffect(() => {
    if (open) setDrafts(initialDrafts);
  }, [open, initialDrafts]);

  const isDirty = (c: Category) => {
    const draft = drafts[c.id];
    if (!draft) return false;
    const original = c.budgetAmount != null ? String(c.budgetAmount) : '';
    return draft.budgetAmount !== original || draft.rolloverEnabled !== c.rolloverEnabled;
  };

  const saveRow = async (c: Category) => {
    const draft = drafts[c.id];
    if (!draft) return;
    const parsed = draft.budgetAmount.trim() === '' ? undefined : Number(draft.budgetAmount);
    if (parsed !== undefined && (!Number.isFinite(parsed) || parsed < 0)) return;
    setSavingId(c.id);
    try {
      await updateCat.mutateAsync({
        id: c.id,
        req: {
          name: c.name,
          icon: c.icon,
          color: c.color,
          budgetAmount: parsed,
          rolloverEnabled: draft.rolloverEnabled,
        },
      });
      setSavedId(c.id);
      setTimeout(() => setSavedId((curr) => (curr === c.id ? null : curr)), 1400);
    } finally {
      setSavingId((curr) => (curr === c.id ? null : curr));
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <button
          type="button"
          title={t('budget.manageCategoryBudgets')}
          className="w-9 h-9 rounded-md border border-input flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer"
        >
          <Settings2 className="w-4 h-4" />
        </button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[520px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('budget.manageCategoryBudgets')}</DialogTitle>
          <p className="text-xs text-muted-foreground mt-1">
            {t('budget.manageCategoryBudgetsHint')}
          </p>
        </DialogHeader>

        {expenseCategories.length === 0 ? (
          <div className="py-12 text-center text-sm text-muted-foreground">
            {t('budget.noExpenseCategories')}
          </div>
        ) : (
          <div className="mt-2 divide-y divide-border/60">
            {expenseCategories.map((c) => {
              const draft = drafts[c.id] ?? { budgetAmount: '', rolloverEnabled: false };
              const dirty = isDirty(c);
              const saving = savingId === c.id;
              const saved = savedId === c.id;
              return (
                <div key={c.id} className="py-3 space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 min-w-0">
                      <span
                        className="w-2 h-2 rounded-full shrink-0"
                        style={{ backgroundColor: c.color ?? 'hsl(var(--muted-foreground))' }}
                      />
                      <span className="text-sm truncate">{c.name}</span>
                    </div>
                    <button
                      type="button"
                      onClick={() =>
                        setDrafts((prev) => ({
                          ...prev,
                          [c.id]: { ...draft, rolloverEnabled: !draft.rolloverEnabled },
                        }))
                      }
                      aria-pressed={draft.rolloverEnabled}
                      className={cn(
                        'flex items-center gap-1.5 h-7 px-2 rounded-full border text-[11px] font-medium transition-colors cursor-pointer',
                        draft.rolloverEnabled
                          ? 'border-sky-500/50 bg-sky-500/10 text-sky-300'
                          : 'border-border text-muted-foreground hover:text-foreground hover:bg-accent'
                      )}
                    >
                      <RotateCcw className="w-3 h-3" />
                      {t('budget.rollover')}
                    </button>
                  </div>
                  <div className="flex items-center gap-2">
                    <Label htmlFor={`budget-${c.id}`} className="text-xs text-muted-foreground w-20">
                      {t('budget.budgetAmount')}
                    </Label>
                    <Input
                      id={`budget-${c.id}`}
                      type="number"
                      inputMode="decimal"
                      min="0"
                      step="0.01"
                      value={draft.budgetAmount}
                      onChange={(e) =>
                        setDrafts((prev) => ({
                          ...prev,
                          [c.id]: { ...draft, budgetAmount: e.target.value },
                        }))
                      }
                      placeholder="0.00"
                      className="h-8 flex-1 font-mono tabular-nums"
                    />
                    <Button
                      type="button"
                      size="sm"
                      variant={dirty ? 'default' : 'outline'}
                      disabled={!dirty || saving}
                      onClick={() => saveRow(c)}
                      className="h-8 min-w-[72px] cursor-pointer"
                    >
                      {saving ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : saved ? (
                        <Check className="w-3.5 h-3.5" />
                      ) : (
                        t('common.save')
                      )}
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
