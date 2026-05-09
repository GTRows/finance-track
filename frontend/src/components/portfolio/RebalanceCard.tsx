import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { AlertTriangle, CheckCircle2, RefreshCw, Scale } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { AccountPicker } from '@/components/accounts/AccountPicker';
import { useAllocation } from '@/hooks/useAllocation';
import { useHoldings } from '@/hooks/useHoldings';
import { useSettings } from '@/hooks/useSettings';
import {
  useRebalanceCommit,
  useRebalancePreview,
  useUpdateRebalanceThreshold,
} from '@/hooks/useRebalance';
import type { ApiError } from '@/types/auth.types';
import type { RebalancePreview, RebalanceSuggestion } from '@/types/rebalance.types';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';

interface RebalanceCardProps {
  portfolioId: string;
}

const MIN_THRESHOLD = 0.1;
const MAX_THRESHOLD = 10;
const STEP = 0.1;
const DEBOUNCE_MS = 400;

export function RebalanceCard({ portfolioId }: RebalanceCardProps) {
  const { t } = useTranslation();
  const allocationQuery = useAllocation(portfolioId);
  const holdingsQuery = useHoldings(portfolioId);
  const settingsQuery = useSettings();

  const initialThreshold =
    (settingsQuery.data as unknown as { rebalanceDriftThresholdPercent?: number } | undefined)
      ?.rebalanceDriftThresholdPercent ?? 1.0;

  const [threshold, setThreshold] = useState<number>(initialThreshold);
  const [accountId, setAccountId] = useState<string | undefined>(undefined);
  const [preview, setPreview] = useState<RebalancePreview | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const previewMutation = useRebalancePreview(portfolioId);
  const commitMutation = useRebalanceCommit(portfolioId);
  const thresholdMutation = useUpdateRebalanceThreshold();

  // Debounced threshold sync to backend
  useEffect(() => {
    if (threshold === initialThreshold) return;
    const handle = window.setTimeout(() => {
      thresholdMutation.mutate({ threshold });
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [threshold]);

  if (
    allocationQuery.isLoading ||
    !allocationQuery.data ||
    !allocationQuery.data.configured ||
    !holdingsQuery.data ||
    holdingsQuery.data.length === 0
  ) {
    return null;
  }

  const handleGenerate = async () => {
    if (!accountId) return;
    setError(null);
    setSuccessMessage(null);
    try {
      const result = await previewMutation.mutateAsync({
        accountId,
        driftThresholdOverride: threshold,
      });
      setPreview(result);
      const defaults = new Set<number>();
      for (const s of result.suggestions) {
        if (s.warning == null && s.assetId != null) defaults.add(s.index);
      }
      setSelected(defaults);
    } catch (err) {
      setPreview(null);
      setError(mapError(err, t));
    }
  };

  const toggleRow = (index: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });
  };

  const handleCommit = async () => {
    if (!preview || !accountId || selected.size === 0) return;
    setError(null);
    setSuccessMessage(null);
    try {
      const result = await commitMutation.mutateAsync({
        proposalId: preview.proposalId,
        accountId,
        selectedIndices: Array.from(selected).sort((a, b) => a - b),
      });
      setSuccessMessage(t('rebalance.successToast', { count: result.committedCount }));
      setPreview(null);
      setSelected(new Set());
    } catch (err) {
      setError(mapError(err, t));
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Scale className="w-5 h-5 text-primary" />
          <CardTitle>{t('rebalance.header')}</CardTitle>
        </div>
        <CardDescription>{t('rebalance.description')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <DriftThresholdSlider value={threshold} onChange={setThreshold} t={t} />

        <AccountPicker
          value={accountId}
          onChange={setAccountId}
          label={t('rebalance.accountPickerLabel')}
        />

        <Button
          onClick={handleGenerate}
          disabled={!accountId || previewMutation.isPending}
          className="cursor-pointer"
        >
          <RefreshCw className={cn('w-4 h-4 mr-2', previewMutation.isPending && 'animate-spin')} />
          {t('rebalance.generateButton')}
        </Button>

        {error && (
          <div className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {successMessage && (
          <div className="flex items-start gap-2 rounded-md border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700 dark:text-emerald-300">
            <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" />
            <span>{successMessage}</span>
          </div>
        )}

        {preview && (
          <RebalanceSuggestionTable
            preview={preview}
            selected={selected}
            onToggle={toggleRow}
            onCommit={handleCommit}
            committing={commitMutation.isPending}
            t={t}
          />
        )}
      </CardContent>
    </Card>
  );
}

interface DriftThresholdSliderProps {
  value: number;
  onChange: (next: number) => void;
  t: ReturnType<typeof useTranslation>['t'];
}

function DriftThresholdSlider({ value, onChange, t }: DriftThresholdSliderProps) {
  return (
    <div className="space-y-1.5">
      <label className="flex items-center justify-between text-sm font-medium">
        <span>{t('rebalance.driftThresholdLabel')}</span>
        <span className="text-muted-foreground">{value.toFixed(2)}%</span>
      </label>
      <input
        type="range"
        min={MIN_THRESHOLD}
        max={MAX_THRESHOLD}
        step={STEP}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        aria-label={t('rebalance.driftThresholdLabel')}
        className="w-full"
      />
      <p className="text-xs text-muted-foreground">{t('rebalance.driftThresholdHint')}</p>
    </div>
  );
}

interface RebalanceSuggestionTableProps {
  preview: RebalancePreview;
  selected: Set<number>;
  onToggle: (index: number) => void;
  onCommit: () => void;
  committing: boolean;
  t: ReturnType<typeof useTranslation>['t'];
}

function RebalanceSuggestionTable({
  preview,
  selected,
  onToggle,
  onCommit,
  committing,
  t,
}: RebalanceSuggestionTableProps) {
  const estimatedCost = useMemo(() => {
    return preview.suggestions
      .filter((s) => selected.has(s.index) && s.action === 'BUY')
      .reduce((acc, s) => acc + s.estimatedAmountTry, 0);
  }, [preview, selected]);

  return (
    <div className="space-y-3">
      {preview.summaryWarnings.length > 0 && (
        <div className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-700 dark:text-amber-300">
          {preview.summaryWarnings
            .map((w) => t(`rebalance.warnings.${w}`, { defaultValue: w }))
            .join(' · ')}
        </div>
      )}
      <div className="overflow-x-auto rounded-md border">
        <table className="w-full text-sm">
          <thead className="bg-muted/50 text-muted-foreground">
            <tr>
              <th className="px-3 py-2 text-left"></th>
              <th className="px-3 py-2 text-left">{t('rebalance.col.symbol')}</th>
              <th className="px-3 py-2 text-left">{t('rebalance.col.assetType')}</th>
              <th className="px-3 py-2 text-right">{t('rebalance.col.currentWeight')}</th>
              <th className="px-3 py-2 text-right">{t('rebalance.col.targetWeight')}</th>
              <th className="px-3 py-2 text-right">{t('rebalance.col.drift')}</th>
              <th className="px-3 py-2 text-left">{t('rebalance.col.action')}</th>
              <th className="px-3 py-2 text-right">{t('rebalance.col.quantity')}</th>
              <th className="px-3 py-2 text-right">{t('rebalance.col.amount')}</th>
              <th className="px-3 py-2 text-left">{t('rebalance.col.warning')}</th>
            </tr>
          </thead>
          <tbody>
            {preview.suggestions.map((s) => (
              <SuggestionRow
                key={s.index}
                suggestion={s}
                checked={selected.has(s.index)}
                onToggle={() => onToggle(s.index)}
                t={t}
              />
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <div className="text-muted-foreground">
          {t('rebalance.summary.selectedRows', {
            selected: selected.size,
            total: preview.suggestions.length,
          })}{' '}
          · {t('rebalance.summary.estimatedCost', { value: formatTRY(estimatedCost) })} ·{' '}
          {t('rebalance.summary.availableCash', { value: formatTRY(preview.accountCashTry) })}
        </div>
        <Button
          onClick={onCommit}
          disabled={selected.size === 0 || committing}
          className="cursor-pointer"
        >
          <CheckCircle2 className="w-4 h-4 mr-2" />
          {t('rebalance.commitButton', { count: selected.size })}
        </Button>
      </div>
    </div>
  );
}

interface SuggestionRowProps {
  suggestion: RebalanceSuggestion;
  checked: boolean;
  onToggle: () => void;
  t: ReturnType<typeof useTranslation>['t'];
}

function SuggestionRow({ suggestion, checked, onToggle, t }: SuggestionRowProps) {
  const disabled = suggestion.assetId == null || suggestion.quantity <= 0;
  return (
    <tr className="border-t">
      <td className="px-3 py-2">
        <input
          type="checkbox"
          aria-label={`select-${suggestion.index}`}
          checked={checked}
          disabled={disabled}
          onChange={onToggle}
          className="cursor-pointer"
        />
      </td>
      <td className="px-3 py-2">{suggestion.symbol ?? '—'}</td>
      <td className="px-3 py-2">{suggestion.assetType}</td>
      <td className="px-3 py-2 text-right">{suggestion.currentWeightPercent.toFixed(2)}%</td>
      <td className="px-3 py-2 text-right">{suggestion.targetWeightPercent.toFixed(2)}%</td>
      <td className="px-3 py-2 text-right">{suggestion.driftPercentBefore.toFixed(2)}%</td>
      <td className="px-3 py-2">{t(`rebalance.actions.${suggestion.action}`)}</td>
      <td className="px-3 py-2 text-right">{suggestion.quantity}</td>
      <td className="px-3 py-2 text-right">{formatTRY(suggestion.estimatedAmountTry)}</td>
      <td className="px-3 py-2 text-amber-600">
        {suggestion.warning
          ? t(`rebalance.warnings.${suggestion.warning}`, { defaultValue: suggestion.warning })
          : ''}
      </td>
    </tr>
  );
}

function mapError(err: unknown, t: ReturnType<typeof useTranslation>['t']): string {
  const axiosError = err as AxiosError<ApiError>;
  const code = axiosError.response?.data?.code;
  if (code) {
    return t(`rebalance.errors.${code}`, { defaultValue: axiosError.response?.data?.error ?? code });
  }
  return t('rebalance.errors.GENERIC');
}
