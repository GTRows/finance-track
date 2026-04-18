import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Split, Plus, Trash2, PlayCircle, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatTRY } from '@/utils/formatters';
import {
  useCashFlowBuckets,
  useCashFlowPreview,
  useReplaceCashFlowBuckets,
} from '@/hooks/useCashFlow';
import { useCategories } from '@/hooks/useBudget';
import type { CashFlowBucket, CashFlowPreview } from '@/types/cashflow.types';

interface DraftBucket {
  id: string;
  name: string;
  percent: string;
  categoryId: string;
}

function toDrafts(buckets: CashFlowBucket[]): DraftBucket[] {
  return buckets.map((b) => ({
    id: b.id,
    name: b.name,
    percent: String(b.percent),
    categoryId: b.categoryId ?? '',
  }));
}

export function CashFlowAllocatorSection() {
  const { t } = useTranslation();
  const bucketsQuery = useCashFlowBuckets();
  const categoriesQuery = useCategories();
  const saveBuckets = useReplaceCashFlowBuckets();
  const preview = useCashFlowPreview();

  const [drafts, setDrafts] = useState<DraftBucket[]>([]);
  const [income, setIncome] = useState('');
  const [obligations, setObligations] = useState('');
  const [result, setResult] = useState<CashFlowPreview | null>(null);

  const expenseCategories = categoriesQuery.data?.expense ?? [];

  useEffect(() => {
    if (bucketsQuery.data) {
      setDrafts(toDrafts(bucketsQuery.data));
    }
  }, [bucketsQuery.data]);

  const totalPercent = useMemo(
    () => drafts.reduce((sum, d) => sum + (parseFloat(d.percent) || 0), 0),
    [drafts],
  );

  const addBucket = () => {
    setDrafts((prev) => [
      ...prev,
      { id: `tmp-${Date.now()}-${prev.length}`, name: '', percent: '', categoryId: '' },
    ]);
  };

  const updateBucket = (id: string, patch: Partial<DraftBucket>) => {
    setDrafts((prev) => prev.map((d) => (d.id === id ? { ...d, ...patch } : d)));
  };

  const removeBucket = (id: string) => {
    setDrafts((prev) => prev.filter((d) => d.id !== id));
  };

  const canSave = drafts.every(
    (d) => d.name.trim().length > 0 && parseFloat(d.percent) >= 0 && parseFloat(d.percent) <= 100,
  );

  const handleSave = () => {
    const payload = drafts.map((d) => ({
      name: d.name.trim(),
      percent: parseFloat(d.percent) || 0,
      categoryId: d.categoryId || undefined,
    }));
    saveBuckets.mutate(payload);
  };

  const handlePreview = async () => {
    const inc = parseFloat(income) || 0;
    const obl = parseFloat(obligations) || 0;
    if (inc <= 0) return;
    const res = await preview.mutateAsync({ income: inc, obligations: obl });
    setResult(res);
  };

  const overTotal = totalPercent > 100;

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-3 space-y-0">
        <div className="min-w-0">
          <CardTitle className="flex items-center gap-2 text-base">
            <Split className="w-4 h-4 text-primary" />
            {t('budget.allocator.title')}
          </CardTitle>
          <p className="text-xs text-muted-foreground mt-1">
            {t('budget.allocator.description')}
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span
            className={cn(
              'text-xs font-mono tabular-nums px-2 py-1 rounded-md border',
              overTotal
                ? 'text-amber-400 border-amber-500/40 bg-amber-500/10'
                : 'text-muted-foreground border-border',
            )}
            title={overTotal ? t('budget.allocator.totalWarn', { total: totalPercent.toFixed(1) }) : undefined}
          >
            {totalPercent.toFixed(1)}%
          </span>
          <Button
            size="sm"
            variant="outline"
            onClick={addBucket}
            className="cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5 mr-1.5" />
            {t('budget.allocator.addBucket')}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        {/* Bucket editor */}
        {drafts.length === 0 ? (
          <div className="py-6 text-center text-sm text-muted-foreground">
            {t('budget.allocator.empty')}
          </div>
        ) : (
          <div className="space-y-2">
            {drafts.map((d) => (
              <div
                key={d.id}
                className="grid grid-cols-[1fr_80px_1fr_32px] items-center gap-2"
              >
                <Input
                  value={d.name}
                  onChange={(e) => updateBucket(d.id, { name: e.target.value })}
                  placeholder={t('budget.allocator.bucketNamePlaceholder')}
                  className="text-sm"
                />
                <div className="relative">
                  <Input
                    type="number"
                    step="0.1"
                    min="0"
                    max="100"
                    value={d.percent}
                    onChange={(e) => updateBucket(d.id, { percent: e.target.value })}
                    className="pr-6 font-mono tabular-nums text-sm"
                  />
                  <span className="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                    %
                  </span>
                </div>
                <select
                  value={d.categoryId}
                  onChange={(e) => updateBucket(d.id, { categoryId: e.target.value })}
                  className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm focus:outline-none focus:ring-1 focus:ring-ring"
                >
                  <option value="">{t('budget.allocator.noCategory')}</option>
                  {expenseCategories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => removeBucket(d.id)}
                  className="w-8 h-8 rounded-md flex items-center justify-center text-muted-foreground hover:text-destructive hover:bg-destructive/10 cursor-pointer transition-colors"
                  title={t('common.delete')}
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}

        <div className="flex items-center justify-between">
          <span className="text-[11px] text-muted-foreground">
            {overTotal && t('budget.allocator.totalWarn', { total: totalPercent.toFixed(1) })}
          </span>
          <Button
            size="sm"
            onClick={handleSave}
            disabled={saveBuckets.isPending || !canSave}
            className="cursor-pointer"
          >
            {saveBuckets.isPending ? t('common.saving') : t('budget.allocator.saveBuckets')}
          </Button>
        </div>

        {/* Preview form */}
        <div className="border-t border-border pt-5 space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label className="text-xs">{t('budget.allocator.income')}</Label>
              <div className="relative">
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  value={income}
                  onChange={(e) => setIncome(e.target.value)}
                  className="pr-10 font-mono tabular-nums"
                  placeholder="0.00"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                  TRY
                </span>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">{t('budget.allocator.obligations')}</Label>
              <div className="relative">
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  value={obligations}
                  onChange={(e) => setObligations(e.target.value)}
                  className="pr-10 font-mono tabular-nums"
                  placeholder="0.00"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                  TRY
                </span>
              </div>
              <p className="text-[10px] text-muted-foreground">
                {t('budget.allocator.obligationsHint')}
              </p>
            </div>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={handlePreview}
            disabled={preview.isPending || !(parseFloat(income) > 0)}
            className="cursor-pointer"
          >
            {preview.isPending ? (
              <Loader2 className="w-3.5 h-3.5 mr-1.5 animate-spin" />
            ) : (
              <PlayCircle className="w-3.5 h-3.5 mr-1.5" />
            )}
            {t('budget.allocator.preview')}
          </Button>
        </div>

        {/* Preview result */}
        {result && (
          <div className="border-t border-border pt-4 space-y-3">
            <div className="grid grid-cols-3 gap-2">
              <MiniStat label={t('budget.allocator.discretionary')} value={result.discretionary} />
              <MiniStat label={t('budget.allocator.assigned')} value={result.assigned} />
              <MiniStat
                label={t('budget.allocator.unassigned')}
                value={result.unassigned}
                valueClass={
                  result.unassigned < 0
                    ? 'text-red-400'
                    : result.unassigned > 0
                      ? 'text-emerald-400'
                      : undefined
                }
              />
            </div>
            <div className="space-y-2">
              {result.buckets.map((b, i) => {
                const pct = result.discretionary > 0
                  ? Math.min(100, (b.amount / result.discretionary) * 100)
                  : 0;
                return (
                  <div key={`${b.name}-${i}`} className="space-y-1">
                    <div className="flex items-center justify-between text-xs">
                      <span className="truncate">{b.name}</span>
                      <div className="flex items-center gap-3 shrink-0">
                        <span className="text-muted-foreground font-mono tabular-nums">
                          {b.percent.toFixed(1)}%
                        </span>
                        <span className="font-mono tabular-nums font-medium">
                          {formatTRY(b.amount)}
                        </span>
                      </div>
                    </div>
                    <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                      <div
                        className="h-full rounded-full bg-primary transition-all duration-500"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function MiniStat({
  label,
  value,
  valueClass,
}: {
  label: string;
  value: number;
  valueClass?: string;
}) {
  return (
    <div className="rounded-md border border-border p-3">
      <p className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className={cn('text-sm font-mono tabular-nums font-medium mt-0.5', valueClass)}>
        {formatTRY(value)}
      </p>
    </div>
  );
}
