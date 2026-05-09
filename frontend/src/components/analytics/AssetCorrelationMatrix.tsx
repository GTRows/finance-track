import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Activity, ChevronDown, Loader2 } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/layout/EmptyState';
import { usePortfolios } from '@/hooks/usePortfolios';
import {
  useCorrelationMatrix,
  useHeldAssets,
  type HeldAssetSummary,
} from '@/hooks/useAnalytics';
import type { CorrelationMethodLiteral } from '@/api/analytics.api';
import { cn } from '@/lib/utils';

const MAX_ASSETS = 25;

type RangePreset = '1M' | '3M' | '6M' | 'YTD' | '90D';

const RANGE_PRESETS: RangePreset[] = ['1M', '3M', '6M', 'YTD', '90D'];

function isoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function presetRange(preset: RangePreset): { from: string; to: string } {
  const now = new Date();
  const to = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (preset === 'YTD') {
    return { from: isoDate(new Date(now.getFullYear(), 0, 1)), to: isoDate(to) };
  }
  if (preset === '90D') {
    const from = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 90);
    return { from: isoDate(from), to: isoDate(to) };
  }
  const monthsBack = preset === '1M' ? 1 : preset === '3M' ? 3 : 6;
  const from = new Date(now.getFullYear(), now.getMonth() - monthsBack, now.getDate());
  return { from: isoDate(from), to: isoDate(to) };
}

/**
 * Linear interpolation between two HSL stops in the project palette. Maps {@code [-1, 0, 1]} to
 * {@code [hsl(0 70% 55%), hsl(var(--muted)), hsl(160 65% 50%)]}. The middle stop uses the muted
 * token to feel native in the project's theme.
 */
function lerpColor(value: number): string {
  const v = Math.max(-1, Math.min(1, value));
  // Negative half: red -> neutral grey (60% lightness).
  if (v < 0) {
    const t = -v; // 0..1
    const lightness = 60 + (55 - 60) * t; // muted -> red lightness
    const saturation = 0 + (70 - 0) * t;
    const hue = 0;
    return `hsl(${hue} ${saturation}% ${lightness}%)`;
  }
  // Positive half: neutral grey -> green.
  const t = v;
  const lightness = 60 + (50 - 60) * t;
  const saturation = 0 + (65 - 0) * t;
  const hue = 160;
  return `hsl(${hue} ${saturation}% ${lightness}%)`;
}

interface HoveredCell {
  i: number;
  j: number;
  x: number;
  y: number;
}

export function AssetCorrelationMatrix() {
  const { t } = useTranslation();
  const portfoliosQuery = usePortfolios();
  const portfolios = useMemo(() => portfoliosQuery.data ?? [], [portfoliosQuery.data]);
  const heldAssets = useHeldAssets(portfolios);

  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [method, setMethod] = useState<CorrelationMethodLiteral>('PEARSON');
  const [preset, setPreset] = useState<RangePreset>('3M');
  const range = useMemo(() => presetRange(preset), [preset]);

  const correlationQuery = useCorrelationMatrix(selectedIds, range.from, range.to, method);

  const [hovered, setHovered] = useState<HoveredCell | null>(null);

  const toggleAsset = (id: string) => {
    setSelectedIds((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id);
      if (prev.length >= MAX_ASSETS) return prev;
      return [...prev, id];
    });
  };

  const isLoading = portfoliosQuery.isLoading || heldAssets.isLoading;

  return (
    <Card>
      <CardHeader className="pb-3 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <CardTitle className="text-sm font-medium">
            {t('analytics.correlations.title')}
          </CardTitle>
          <CardDescription className="text-xs">
            {t('analytics.correlations.description')}
          </CardDescription>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <MethodToggle value={method} onChange={setMethod} />
          <RangePresetRow value={preset} onChange={setPreset} />
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex flex-wrap items-center gap-2 mb-4">
          <AssetMultiSelect
            assets={heldAssets.data}
            selectedIds={selectedIds}
            onToggle={toggleAsset}
          />
          {correlationQuery.isFetching ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin text-muted-foreground" />
          ) : null}
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center h-[260px]">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        ) : heldAssets.data.length < 2 ? (
          <EmptyState
            icon={Activity}
            title={t('analytics.correlations.emptyTitle')}
            description={t('analytics.correlations.noAssetsHeld')}
          />
        ) : selectedIds.length < 2 ? (
          <EmptyState
            icon={Activity}
            title={t('analytics.correlations.emptyTitle')}
            description={t('analytics.correlations.emptyDesc')}
          />
        ) : correlationQuery.isError ? (
          <EmptyState
            icon={Activity}
            title={t('analytics.correlations.loadingError')}
            description={t('analytics.correlations.emptyDesc')}
          />
        ) : correlationQuery.data ? (
          <Heatmap
            data={correlationQuery.data}
            method={method}
            onCellHover={setHovered}
            hovered={hovered}
          />
        ) : (
          <div className="flex items-center justify-center h-[260px]">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        )}
        <p className="text-[11px] text-muted-foreground mt-3 leading-relaxed">
          {t('analytics.correlations.retentionFootnote')}
        </p>
      </CardContent>
    </Card>
  );
}

interface HeatmapProps {
  data: import('@/api/analytics.api').CorrelationMatrixResponse;
  method: CorrelationMethodLiteral;
  onCellHover: (cell: HoveredCell | null) => void;
  hovered: HoveredCell | null;
}

function Heatmap({ data, method, onCellHover, hovered }: HeatmapProps) {
  const { t } = useTranslation();
  const n = data.assetIds.length;
  const symbols = data.assetSymbols;

  const gridStyle = useMemo<React.CSSProperties>(
    () => ({
      display: 'grid',
      gridTemplateColumns: `auto repeat(${n}, minmax(28px, 1fr))`,
      gap: '1px',
    }),
    [n],
  );

  return (
    <div className="relative">
      <div role="grid" aria-label={t('analytics.correlations.matrixAriaLabel')} style={gridStyle}>
        <div />
        {symbols.map((s, j) => (
          <div
            key={`col-${j}`}
            role="columnheader"
            className="text-[10px] font-mono tabular-nums text-muted-foreground text-center px-1 py-1 truncate"
            title={data.assetNames[j]}
          >
            {s}
          </div>
        ))}
        {symbols.map((rowSymbol, i) => (
          <RowFragment
            key={`row-${i}`}
            i={i}
            n={n}
            rowSymbol={rowSymbol}
            rowName={data.assetNames[i]}
            row={data.matrix[i]}
            dataPoints={data.dataPoints[i]}
            onCellHover={onCellHover}
          />
        ))}
      </div>
      {hovered ? (
        <CorrelationTooltip
          data={data}
          method={method}
          i={hovered.i}
          j={hovered.j}
          x={hovered.x}
          y={hovered.y}
        />
      ) : null}
    </div>
  );
}

interface RowFragmentProps {
  i: number;
  n: number;
  rowSymbol: string;
  rowName: string;
  row: Array<number | null>;
  dataPoints: number[];
  onCellHover: (cell: HoveredCell | null) => void;
}

function RowFragment({ i, n, rowSymbol, rowName, row, onCellHover }: RowFragmentProps) {
  return (
    <>
      <div
        role="rowheader"
        className="text-[10px] font-mono tabular-nums text-muted-foreground text-right pr-2 py-1 truncate"
        title={rowName}
      >
        {rowSymbol}
      </div>
      {Array.from({ length: n }).map((_, j) => (
        <Cell
          key={`cell-${i}-${j}`}
          i={i}
          j={j}
          value={row[j]}
          onHover={onCellHover}
        />
      ))}
    </>
  );
}

interface CellProps {
  i: number;
  j: number;
  value: number | null;
  onHover: (cell: HoveredCell | null) => void;
}

function Cell({ i, j, value, onHover }: CellProps) {
  const { t } = useTranslation();
  const isDiagonal = i === j;

  if (isDiagonal) {
    return (
      <div
        role="gridcell"
        aria-label={`${i},${j}`}
        className="aspect-square bg-muted text-muted-foreground text-[10px] font-mono tabular-nums flex items-center justify-center select-none"
      >
        —
      </div>
    );
  }

  if (value == null) {
    return (
      <button
        type="button"
        role="gridcell"
        aria-label={t('analytics.correlations.naTooltip')}
        className="aspect-square text-[10px] font-mono tabular-nums text-muted-foreground flex items-center justify-center cursor-help"
        style={{
          backgroundImage:
            'repeating-linear-gradient(45deg, hsl(var(--muted)), hsl(var(--muted)) 4px, hsl(var(--background)) 4px, hsl(var(--background)) 6px)',
        }}
        onMouseEnter={(e) => onHover({ i, j, x: e.clientX, y: e.clientY })}
        onMouseLeave={() => onHover(null)}
        onFocus={(e) =>
          onHover({
            i,
            j,
            x: e.currentTarget.getBoundingClientRect().left,
            y: e.currentTarget.getBoundingClientRect().top,
          })
        }
        onBlur={() => onHover(null)}
      >
        {t('analytics.correlations.naLabel')}
      </button>
    );
  }

  const showText = true;
  return (
    <button
      type="button"
      role="gridcell"
      aria-label={`${i},${j}: ${value.toFixed(3)}`}
      className={cn(
        'aspect-square text-[10px] font-mono tabular-nums flex items-center justify-center cursor-help select-none',
        Math.abs(value) > 0.5 ? 'text-background' : 'text-foreground',
      )}
      style={{ backgroundColor: lerpColor(value) }}
      onMouseEnter={(e) => onHover({ i, j, x: e.clientX, y: e.clientY })}
      onMouseLeave={() => onHover(null)}
      onFocus={(e) =>
        onHover({
          i,
          j,
          x: e.currentTarget.getBoundingClientRect().left,
          y: e.currentTarget.getBoundingClientRect().top,
        })
      }
      onBlur={() => onHover(null)}
    >
      {showText ? value.toFixed(2) : ''}
    </button>
  );
}

interface CorrelationTooltipProps {
  data: import('@/api/analytics.api').CorrelationMatrixResponse;
  method: CorrelationMethodLiteral;
  i: number;
  j: number;
  x: number;
  y: number;
}

function CorrelationTooltip({ data, method, i, j, x, y }: CorrelationTooltipProps) {
  const { t } = useTranslation();
  const value = data.matrix[i][j];
  const points = data.dataPoints[i][j];
  const top = Math.max(8, y - 80);
  const left = Math.min(window.innerWidth - 240, x + 12);
  const methodLabel =
    method === 'PEARSON'
      ? t('analytics.correlations.methodPearson')
      : t('analytics.correlations.methodSpearman');

  return (
    <div
      className="fixed pointer-events-none z-50 rounded-md border border-border bg-card/95 backdrop-blur px-3 py-2 shadow-lg shadow-black/20 text-xs min-w-[200px]"
      style={{ top, left }}
    >
      <p className="text-[10px] uppercase tracking-[0.14em] text-muted-foreground mb-1">
        {t('analytics.correlations.tooltipPair')}
      </p>
      <p className="font-mono tabular-nums mb-1.5">
        {data.assetSymbols[i]} <span className="text-muted-foreground">·</span>{' '}
        {data.assetSymbols[j]}
      </p>
      <div className="flex items-baseline justify-between gap-3 mb-0.5">
        <span className="text-muted-foreground">
          {t('analytics.correlations.tooltipValue')}
        </span>
        <span className="font-mono tabular-nums font-semibold">
          {value == null ? t('analytics.correlations.naLabel') : value.toFixed(3)}
        </span>
      </div>
      <div className="flex items-baseline justify-between gap-3 mb-0.5">
        <span className="text-muted-foreground">
          {t('analytics.correlations.tooltipDataPoints')}
        </span>
        <span className="font-mono tabular-nums">{points}</span>
      </div>
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-muted-foreground">
          {t('analytics.correlations.tooltipMethod')}
        </span>
        <span className="font-mono tabular-nums">{methodLabel}</span>
      </div>
    </div>
  );
}

interface MethodToggleProps {
  value: CorrelationMethodLiteral;
  onChange: (method: CorrelationMethodLiteral) => void;
}

function MethodToggle({ value, onChange }: MethodToggleProps) {
  const { t } = useTranslation();
  const modes: Array<{ key: CorrelationMethodLiteral; labelKey: string }> = [
    { key: 'PEARSON', labelKey: 'analytics.correlations.methodPearson' },
    { key: 'SPEARMAN', labelKey: 'analytics.correlations.methodSpearman' },
  ];
  return (
    <div className="inline-flex items-center gap-0.5 rounded-md border border-border bg-background/40 p-0.5">
      {modes.map((m) => (
        <button
          key={m.key}
          type="button"
          onClick={() => onChange(m.key)}
          className={cn(
            'h-7 px-2.5 rounded text-[11px] font-medium tracking-tight transition-colors cursor-pointer',
            value === m.key
              ? 'bg-primary/15 text-primary'
              : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {t(m.labelKey)}
        </button>
      ))}
    </div>
  );
}

interface RangePresetRowProps {
  value: RangePreset;
  onChange: (preset: RangePreset) => void;
}

function RangePresetRow({ value, onChange }: RangePresetRowProps) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-1">
      {RANGE_PRESETS.map((p) => (
        <button
          key={p}
          type="button"
          onClick={() => onChange(p)}
          className={cn(
            'h-7 px-2.5 rounded-md border text-[11px] font-medium tabular-nums transition-colors cursor-pointer',
            value === p
              ? 'bg-primary/10 border-primary/40 text-primary'
              : 'border-border text-muted-foreground hover:text-foreground hover:border-border/80',
          )}
        >
          {t(`analytics.correlations.range${p}`)}
        </button>
      ))}
    </div>
  );
}

interface AssetMultiSelectProps {
  assets: HeldAssetSummary[];
  selectedIds: string[];
  onToggle: (id: string) => void;
}

function AssetMultiSelect({ assets, selectedIds, onToggle }: AssetMultiSelectProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const selectionLabel =
    selectedIds.length === 0
      ? t('analytics.correlations.selectAssetsPlaceholder')
      : t('analytics.correlations.selectAssets', { count: selectedIds.length });

  const atCap = selectedIds.length >= MAX_ASSETS;

  return (
    <div ref={rootRef} className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="inline-flex items-center gap-2 h-9 px-3 rounded-md border border-border bg-background/40 text-sm text-foreground hover:border-primary/50 hover:bg-accent/40 transition-colors cursor-pointer"
      >
        <span className="tracking-tight">{selectionLabel}</span>
        <ChevronDown className="w-3.5 h-3.5 text-muted-foreground" />
      </button>
      {open ? (
        <div className="absolute left-0 mt-2 z-30 w-[300px] max-h-[320px] overflow-y-auto rounded-lg border border-border bg-card shadow-xl shadow-black/30 p-2">
          {assets.length === 0 ? (
            <p className="text-xs text-muted-foreground px-2 py-3 text-center">
              {t('analytics.correlations.noAssetsHeld')}
            </p>
          ) : (
            <ul className="space-y-0.5">
              {assets.map((a) => {
                const checked = selectedIds.includes(a.assetId);
                const disabled = !checked && atCap;
                return (
                  <li key={a.assetId}>
                    <label
                      className={cn(
                        'flex items-center gap-2 h-8 px-2 rounded-md text-xs cursor-pointer transition-colors',
                        disabled ? 'opacity-50 cursor-not-allowed' : 'hover:bg-accent/40',
                      )}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={disabled}
                        onChange={() => onToggle(a.assetId)}
                        className="w-3.5 h-3.5 rounded border-border accent-primary"
                      />
                      <span className="font-mono tabular-nums text-muted-foreground w-12 truncate">
                        {a.symbol}
                      </span>
                      <span className="truncate">{a.name}</span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  );
}
