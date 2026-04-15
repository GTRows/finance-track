import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Plus, Trash2, ShieldAlert } from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatCurrency } from '@/utils/formatters';
import {
  useBudgetRules,
  useCreateBudgetRule,
  useDeleteBudgetRule,
} from '@/hooks/useBudgetRules';
import { useCategories } from '@/hooks/useBudget';

export function BudgetRulesSection() {
  const { t } = useTranslation();
  const rulesQuery = useBudgetRules();
  const categoriesQuery = useCategories();
  const createRule = useCreateBudgetRule();
  const deleteRule = useDeleteBudgetRule();
  const [open, setOpen] = useState(false);
  const [categoryId, setCategoryId] = useState('');
  const [limit, setLimit] = useState('');
  const [confirming, setConfirming] = useState<string | null>(null);

  const rules = rulesQuery.data ?? [];
  const categories = categoriesQuery.data?.expense ?? [];

  const reset = () => {
    setCategoryId('');
    setLimit('');
  };

  const valid = categoryId && parseFloat(limit) > 0;

  const handleSubmit = () => {
    if (!valid) return;
    createRule.mutate(
      { categoryId, monthlyLimitTry: parseFloat(limit) },
      {
        onSuccess: () => {
          reset();
          setOpen(false);
        },
      }
    );
  };

  const handleDelete = (id: string) => {
    if (confirming === id) {
      deleteRule.mutate(id);
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
            <ShieldAlert className="w-4 h-4 text-primary" />
            {t('budgetRules.title')}
          </CardTitle>
          <p className="text-xs text-muted-foreground mt-1">
            {t('budgetRules.description')}
          </p>
        </div>
        <Dialog open={open} onOpenChange={(v) => { setOpen(v); if (!v) reset(); }}>
          <DialogTrigger asChild>
            <Button size="sm" variant="outline" className="cursor-pointer">
              <Plus className="w-3.5 h-3.5 mr-1.5" />
              {t('budgetRules.add')}
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[400px]">
            <DialogHeader>
              <DialogTitle>{t('budgetRules.add')}</DialogTitle>
            </DialogHeader>
            <div className="space-y-4 pt-2">
              <div className="space-y-1.5">
                <Label>{t('budgetRules.category')}</Label>
                <select
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm focus:outline-none focus:ring-1 focus:ring-ring"
                >
                  <option value="">--</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-1.5">
                <Label>{t('budgetRules.monthlyLimit')}</Label>
                <div className="relative">
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="0.00"
                    value={limit}
                    onChange={(e) => setLimit(e.target.value)}
                    className="pr-10 font-mono tabular-nums"
                  />
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                    TRY
                  </span>
                </div>
              </div>
              <Button
                onClick={handleSubmit}
                disabled={createRule.isPending || !valid}
                className="w-full cursor-pointer"
              >
                {createRule.isPending ? t('common.saving') : t('common.save')}
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </CardHeader>
      <CardContent>
        {rules.length === 0 ? (
          <div className="py-8 text-center text-sm text-muted-foreground">
            {t('budgetRules.empty')}
          </div>
        ) : (
          <div className="space-y-3">
            {rules.map((rule) => {
              const usage = Math.min(rule.usagePct, 999);
              const over = rule.usagePct >= 100;
              const warn = !over && rule.usagePct >= 80;
              const barColor = over
                ? 'bg-rose-500'
                : warn
                  ? 'bg-amber-500'
                  : 'bg-emerald-500';
              return (
                <div key={rule.id} className="space-y-1.5">
                  <div className="flex items-center gap-3">
                    <div
                      className="w-2 h-2 rounded-full flex-shrink-0"
                      style={{ background: rule.categoryColor ?? '#888' }}
                    />
                    <span className="text-sm font-medium flex-1 truncate">
                      {rule.categoryName}
                    </span>
                    <span
                      className={cn(
                        'text-xs font-mono tabular-nums',
                        over ? 'text-rose-500' : warn ? 'text-amber-500' : 'text-muted-foreground'
                      )}
                    >
                      {formatCurrency(rule.currentSpendTry)} / {formatCurrency(rule.monthlyLimitTry)}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleDelete(rule.id)}
                      className={cn(
                        'w-7 h-7 rounded-md flex items-center justify-center transition-colors cursor-pointer',
                        confirming === rule.id
                          ? 'bg-rose-500/15 text-rose-500'
                          : 'text-muted-foreground hover:text-rose-500 hover:bg-rose-500/10'
                      )}
                      title={
                        confirming === rule.id ? t('alerts.confirmDelete') : t('common.delete')
                      }
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                    <div
                      className={cn('h-full rounded-full transition-all', barColor)}
                      style={{ width: `${Math.min(usage, 100)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
