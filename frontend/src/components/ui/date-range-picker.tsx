/* eslint-disable react-refresh/only-export-components */
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Calendar, X } from 'lucide-react';
import { cn } from '@/lib/utils';

export type PresetKey = '3M' | '6M' | 'YTD' | '1Y' | 'ALL' | 'CUSTOM';

export interface DateRange {
  from: Date | null;
  to: Date | null;
  preset: PresetKey;
}

interface DateRangePickerProps {
  value: DateRange;
  onChange: (range: DateRange) => void;
  className?: string;
}

const PRESETS: PresetKey[] = ['3M', '6M', 'YTD', '1Y', 'ALL'];

function computePresetRange(preset: PresetKey): DateRange {
  const now = new Date();
  const to = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (preset === 'ALL') return { from: null, to: null, preset };
  if (preset === 'YTD') {
    return { from: new Date(now.getFullYear(), 0, 1), to, preset };
  }
  const monthsBack = preset === '3M' ? 3 : preset === '6M' ? 6 : preset === '1Y' ? 12 : 0;
  const from = new Date(now.getFullYear(), now.getMonth() - monthsBack, now.getDate());
  return { from, to, preset };
}

export function defaultDateRange(): DateRange {
  return computePresetRange('ALL');
}

function toInputValue(d: Date | null): string {
  if (!d) return '';
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function parseInputValue(v: string): Date | null {
  if (!v) return null;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function DateRangePicker({ value, onChange, className }: DateRangePickerProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [customFrom, setCustomFrom] = useState(toInputValue(value.from));
  const [customTo, setCustomTo] = useState(toInputValue(value.to));

  useEffect(() => {
    setCustomFrom(toInputValue(value.from));
    setCustomTo(toInputValue(value.to));
  }, [value.from, value.to]);

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

  const buttonLabel = useMemo(() => {
    if (value.preset !== 'CUSTOM') return t(`dateRange.preset.${value.preset}`);
    if (!value.from && !value.to) return t('dateRange.preset.ALL');
    const fmt = new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
    if (value.from && value.to) return `${fmt.format(value.from)} - ${fmt.format(value.to)}`;
    if (value.from) return t('dateRange.since', { date: fmt.format(value.from) });
    if (value.to) return t('dateRange.until', { date: fmt.format(value.to) });
    return t('dateRange.preset.ALL');
  }, [value, t]);

  const applyCustom = () => {
    const from = parseInputValue(customFrom);
    const to = parseInputValue(customTo);
    if (from && to && from.getTime() > to.getTime()) return;
    onChange({ from, to, preset: 'CUSTOM' });
    setOpen(false);
  };

  return (
    <div ref={rootRef} className={cn('relative inline-block', className)}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className={cn(
          'inline-flex items-center gap-2 h-9 px-3 rounded-md border border-border bg-background/40 text-sm text-foreground hover:border-primary/50 hover:bg-accent/40 transition-colors cursor-pointer'
        )}
      >
        <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
        <span className="tracking-tight">{buttonLabel}</span>
      </button>

      {open ? (
        <div
          className={cn(
            'absolute right-0 mt-2 z-30 w-[300px] rounded-lg border border-border bg-card shadow-xl shadow-black/30 p-3'
          )}
        >
          <div className="flex flex-wrap gap-1.5 pb-3 border-b border-border/60">
            {PRESETS.map((p) => {
              const active = value.preset === p;
              return (
                <button
                  key={p}
                  type="button"
                  onClick={() => {
                    onChange(computePresetRange(p));
                    setOpen(false);
                  }}
                  className={cn(
                    'h-7 px-2.5 text-xs rounded-md font-medium tracking-tight transition-colors cursor-pointer',
                    active
                      ? 'bg-primary/15 text-primary'
                      : 'text-muted-foreground hover:text-foreground hover:bg-accent/50'
                  )}
                >
                  {t(`dateRange.preset.${p}`)}
                </button>
              );
            })}
          </div>

          <div className="pt-3 space-y-2">
            <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground/70">
              {t('dateRange.custom')}
            </div>
            <div className="grid grid-cols-2 gap-2">
              <label className="space-y-1">
                <span className="text-[11px] text-muted-foreground">{t('dateRange.from')}</span>
                <input
                  type="date"
                  value={customFrom}
                  onChange={(e) => setCustomFrom(e.target.value)}
                  className="w-full h-8 px-2 text-xs rounded-md border border-border bg-background/40 outline-none focus:border-primary/60"
                />
              </label>
              <label className="space-y-1">
                <span className="text-[11px] text-muted-foreground">{t('dateRange.to')}</span>
                <input
                  type="date"
                  value={customTo}
                  onChange={(e) => setCustomTo(e.target.value)}
                  className="w-full h-8 px-2 text-xs rounded-md border border-border bg-background/40 outline-none focus:border-primary/60"
                />
              </label>
            </div>
            <div className="flex items-center justify-between gap-2 pt-1">
              <button
                type="button"
                onClick={() => {
                  setCustomFrom('');
                  setCustomTo('');
                  onChange(computePresetRange('ALL'));
                  setOpen(false);
                }}
                className="inline-flex items-center gap-1 h-7 px-2 text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
              >
                <X className="w-3 h-3" />
                {t('dateRange.clear')}
              </button>
              <button
                type="button"
                onClick={applyCustom}
                className="h-7 px-3 text-xs font-medium rounded-md bg-primary text-primary-foreground hover:bg-primary/90 transition-colors cursor-pointer"
              >
                {t('dateRange.apply')}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function filterByRange<T>(items: T[], getDate: (item: T) => string, range: DateRange): T[] {
  if (!range.from && !range.to) return items;
  const fromTs = range.from ? range.from.getTime() : -Infinity;
  const toTs = range.to ? new Date(range.to.getFullYear(), range.to.getMonth(), range.to.getDate(), 23, 59, 59, 999).getTime() : Infinity;
  return items.filter((item) => {
    const ts = new Date(getDate(item)).getTime();
    return ts >= fromTs && ts <= toTs;
  });
}
