import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { Target, Pencil, Save, X, TriangleAlert } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAllocation, useSetAllocation } from '@/hooks/useAllocation';
import type { AllocationRow } from '@/types/allocation.types';
import type { AssetType } from '@/types/portfolio.types';
import type { ApiError } from '@/types/auth.types';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';

const ALL_TYPES: AssetType[] = ['CRYPTO', 'FUND', 'CURRENCY', 'GOLD', 'STOCK', 'OTHER'];
const DRIFT_WARN = 3;

interface AllocationTargetsProps {
  portfolioId: string;
}

export function AllocationTargets({ portfolioId }: AllocationTargetsProps) {
  const { t } = useTranslation();
  const { data, isLoading } = useAllocation(portfolioId);
  const mutation = useSetAllocation(portfolioId);
  const [editing, setEditing] = useState(false);
  const [drafts, setDrafts] = useState<Record<AssetType, string>>(emptyDrafts());
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!editing && data) {
      const next = emptyDrafts();
      for (const row of data.rows) {
        next[row.assetType] = row.targetPercent > 0 ? String(row.targetPercent) : '';
      }
      setDrafts(next);
    }
  }, [data, editing]);

  const draftSum = useMemo(() => {
    return ALL_TYPES.reduce((acc, type) => {
      const raw = drafts[type]?.replace(',', '.') ?? '';
      const n = Number(raw);
      return acc + (Number.isFinite(n) && n > 0 ? n : 0);
    }, 0);
  }, [drafts]);

  if (isLoading || !data) return null;

  const rows = data.rows;
  const visibleRows = rows.length > 0 ? rows : emptyPreview();
  const sumValid = Math.abs(draftSum - 100) < 0.05 || !editing;

  const handleSave = async () => {
    setError(null);
    const targets = ALL_TYPES.map((assetType) => {
      const raw = drafts[assetType]?.replace(',', '.') ?? '';
      const n = Number(raw);
      return { assetType, targetPercent: Number.isFinite(n) && n > 0 ? n : 0 };
    }).filter((t) => t.targetPercent > 0);

    if (targets.length > 0) {
      const sum = targets.reduce((acc, t) => acc + t.targetPercent, 0);
      if (Math.abs(sum - 100) > 0.05) {
        setError(t('allocation.sumError', { sum: sum.toFixed(2) }));
        return;
      }
    }

    try {
      await mutation.mutateAsync({ targets });
      setEditing(false);
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error || t('allocation.saveError'));
    }
  };

  const handleClear = () => {
    setDrafts(emptyDrafts());
  };

  const handleCancel = () => {
    setEditing(false);
    setError(null);
  };

  return (
    <Card>
      <CardHeader className="pb-4">
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-1 min-w-0">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Target className="w-4 h-4 text-primary" />
              {t('allocation.title')}
            </CardTitle>
            <CardDescription className="text-xs">{t('allocation.description')}</CardDescription>
          </div>
          {!editing ? (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setEditing(true)}
              className="flex-shrink-0"
            >
              <Pencil className="w-3.5 h-3.5 mr-1.5" />
              {data.configured ? t('allocation.edit') : t('allocation.setTargets')}
            </Button>
          ) : (
            <div className="flex items-center gap-1 flex-shrink-0">
              <Button size="sm" variant="ghost" onClick={handleCancel} disabled={mutation.isPending}>
                <X className="w-3.5 h-3.5 mr-1.5" />
                {t('common.cancel')}
              </Button>
              <Button
                size="sm"
                onClick={handleSave}
                disabled={mutation.isPending || !sumValid}
              >
                <Save className="w-3.5 h-3.5 mr-1.5" />
                {t('common.save')}
              </Button>
            </div>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {editing ? (
          <EditMode
            drafts={drafts}
            setDrafts={setDrafts}
            draftSum={draftSum}
            onClear={handleClear}
            error={error}
          />
        ) : data.configured ? (
          <ReadMode rows={visibleRows} totalValue={data.totalValueTry} />
        ) : (
          <EmptyHint />
        )}
      </CardContent>
    </Card>
  );
}

function ReadMode({
  rows,
  totalValue,
}: {
  rows: AllocationRow[];
  totalValue: number;
}) {
  const { t } = useTranslation();
  return (
    <div className="space-y-3">
      {rows.map((row) => (
        <AllocationBar key={row.assetType} row={row} totalValue={totalValue} />
      ))}
      <div className="pt-3 mt-3 border-t border-border/40 flex items-center justify-between text-xs text-muted-foreground">
        <span>{t('allocation.totalValue')}</span>
        <span className="font-mono tabular-nums">{formatTRY(totalValue)}</span>
      </div>
    </div>
  );
}

function AllocationBar({ row, totalValue }: { row: AllocationRow; totalValue: number }) {
  const { t } = useTranslation();
  const drift = Number(row.driftPercent);
  const flagged = Math.abs(drift) >= DRIFT_WARN;
  const overweight = drift > 0;
  const target = Number(row.targetPercent);
  const actual = Number(row.actualPercent);
  const maxScale = Math.max(100, target, actual);

  return (
    <div>
      <div className="flex items-center justify-between text-sm mb-1.5">
        <div className="flex items-center gap-2">
          <span className="text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-muted text-muted-foreground font-medium">
            {t(`prices.filter.${row.assetType}`)}
          </span>
          <span className="font-mono tabular-nums text-muted-foreground text-xs">
            {formatTRY(Number(row.actualValueTry))}
          </span>
        </div>
        <div className="flex items-center gap-2 font-mono tabular-nums text-xs">
          <span className="text-muted-foreground">
            {actual.toFixed(1)}% / <span className="text-foreground/60">{target.toFixed(1)}%</span>
          </span>
          <span
            className={cn(
              'inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-medium w-20 justify-center',
              flagged
                ? overweight
                  ? 'bg-rose-500/15 text-rose-600 dark:text-rose-400'
                  : 'bg-amber-500/15 text-amber-700 dark:text-amber-400'
                : 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
            )}
          >
            {flagged && <TriangleAlert className="w-3 h-3" />}
            {drift > 0 ? '+' : ''}
            {drift.toFixed(1)}%
          </span>
        </div>
      </div>
      <div className="relative h-2 rounded-full bg-muted overflow-hidden">
        <div
          className="absolute inset-y-0 left-0 bg-primary/70 rounded-full transition-all"
          style={{ width: `${Math.min(100, (actual / maxScale) * 100)}%` }}
        />
        <div
          className="absolute top-0 bottom-0 w-[2px] bg-foreground/70"
          style={{ left: `calc(${Math.min(100, (target / maxScale) * 100)}% - 1px)` }}
          title={`${t('allocation.target')}: ${target.toFixed(1)}%`}
        />
      </div>
      {flagged && (
        <p className="text-[11px] text-muted-foreground mt-1">
          {overweight
            ? t('allocation.overweightHint', {
                amount: formatTRY(Math.abs(Number(row.driftValueTry))),
                type: t(`prices.filter.${row.assetType}`),
              })
            : t('allocation.underweightHint', {
                amount: formatTRY(Math.abs(Number(row.driftValueTry))),
                type: t(`prices.filter.${row.assetType}`),
              })}
        </p>
      )}
      <div className="flex items-center justify-end text-[10px] text-muted-foreground mt-1">
        <span>{formatTRY(Number(row.actualValueTry))} / {formatTRY(totalValue)}</span>
      </div>
    </div>
  );
}

function EditMode({
  drafts,
  setDrafts,
  draftSum,
  onClear,
  error,
}: {
  drafts: Record<AssetType, string>;
  setDrafts: (next: Record<AssetType, string>) => void;
  draftSum: number;
  onClear: () => void;
  error: string | null;
}) {
  const { t } = useTranslation();
  const sumOk = Math.abs(draftSum - 100) < 0.05;
  const sumEmpty = draftSum === 0;

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {ALL_TYPES.map((type) => (
          <div key={type} className="flex items-center gap-3">
            <span className="text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-muted text-muted-foreground font-medium w-20 text-center flex-shrink-0">
              {t(`prices.filter.${type}`)}
            </span>
            <div className="relative flex-1">
              <Input
                type="text"
                inputMode="decimal"
                value={drafts[type] ?? ''}
                onChange={(e) => setDrafts({ ...drafts, [type]: e.target.value })}
                className="h-9 pr-8 font-mono tabular-nums text-right"
                placeholder="0"
              />
              <span className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">%</span>
            </div>
          </div>
        ))}
      </div>
      <div
        className={cn(
          'flex items-center justify-between text-xs border-t border-border/40 pt-3',
          sumEmpty
            ? 'text-muted-foreground'
            : sumOk
              ? 'text-emerald-600 dark:text-emerald-400'
              : 'text-rose-600 dark:text-rose-400'
        )}
      >
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onClear}
            className="text-muted-foreground hover:text-foreground underline-offset-2 hover:underline"
          >
            {t('allocation.clearAll')}
          </button>
          {sumEmpty && <span>{t('allocation.clearHint')}</span>}
        </div>
        <span className="font-mono tabular-nums">
          {t('allocation.sum')}: {draftSum.toFixed(2)}% / 100.00%
        </span>
      </div>
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}

function EmptyHint() {
  const { t } = useTranslation();
  return (
    <div className="py-6 text-center text-sm text-muted-foreground space-y-2">
      <Target className="w-6 h-6 mx-auto text-muted-foreground/50" />
      <p>{t('allocation.emptyTitle')}</p>
      <p className="text-xs">{t('allocation.emptyHint')}</p>
    </div>
  );
}

function emptyDrafts(): Record<AssetType, string> {
  const out: Partial<Record<AssetType, string>> = {};
  for (const t of ALL_TYPES) out[t] = '';
  return out as Record<AssetType, string>;
}

function emptyPreview(): AllocationRow[] {
  return [];
}
