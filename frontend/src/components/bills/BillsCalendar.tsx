import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { Bill } from '@/types/bill.types';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';

interface BillsCalendarProps {
  bills: Bill[];
}

interface DayCell {
  date: Date;
  inMonth: boolean;
  bills: Bill[];
}

export function BillsCalendar({ bills }: BillsCalendarProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage === 'tr' ? 'tr-TR' : 'en-US';

  const { cells, monthLabel } = useMemo(() => computeMonthGrid(bills, locale), [bills, locale]);

  const weekdayLabels = useMemo(() => {
    const base = new Date(2024, 0, 1);
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(base);
      d.setDate(base.getDate() + i);
      return new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(d);
    });
  }, [locale]);

  const today = new Date();
  const todayKey = dayKey(today);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium capitalize">{monthLabel}</h3>
        <span className="text-xs text-muted-foreground">{t('bills.calendarHint')}</span>
      </div>

      <div className="grid grid-cols-7 gap-1">
        {weekdayLabels.map((label) => (
          <div
            key={label}
            className="text-[10px] uppercase tracking-wider text-muted-foreground text-center py-1"
          >
            {label}
          </div>
        ))}

        {cells.map((cell, idx) => {
          const isToday = dayKey(cell.date) === todayKey;
          const hasBills = cell.bills.length > 0;
          return (
            <div
              key={idx}
              className={cn(
                'min-h-[64px] rounded-md border p-1.5 flex flex-col gap-1 text-left transition-colors',
                cell.inMonth ? 'bg-card' : 'bg-muted/20 opacity-50',
                isToday && 'border-primary ring-1 ring-primary/40',
                !isToday && 'border-border'
              )}
            >
              <span
                className={cn(
                  'text-[11px] font-medium',
                  isToday && 'text-primary',
                  !cell.inMonth && 'text-muted-foreground'
                )}
              >
                {cell.date.getDate()}
              </span>
              {hasBills && (
                <div className="space-y-0.5 overflow-hidden">
                  {cell.bills.slice(0, 2).map((bill) => (
                    <div
                      key={bill.id}
                      title={`${bill.name} - ${formatTRY(bill.amount)}`}
                      className={cn(
                        'text-[10px] leading-tight px-1 py-0.5 rounded truncate font-medium',
                        bill.currentPeriodStatus === 'PAID'
                          ? 'bg-emerald-500/15 text-emerald-400'
                          : bill.daysUntilDue <= 3 && bill.daysUntilDue >= 0
                            ? 'bg-red-500/15 text-red-400'
                            : 'bg-amber-500/15 text-amber-400'
                      )}
                    >
                      {bill.name}
                    </div>
                  ))}
                  {cell.bills.length > 2 && (
                    <div className="text-[10px] text-muted-foreground px-1">
                      +{cell.bills.length - 2}
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function dayKey(d: Date): string {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

function computeMonthGrid(bills: Bill[], locale: string): { cells: DayCell[]; monthLabel: string } {
  const today = new Date();
  const year = today.getFullYear();
  const month = today.getMonth();
  const firstOfMonth = new Date(year, month, 1);
  const lastOfMonth = new Date(year, month + 1, 0);

  const weekday = (firstOfMonth.getDay() + 6) % 7;
  const gridStart = new Date(firstOfMonth);
  gridStart.setDate(firstOfMonth.getDate() - weekday);

  const totalDays = Math.ceil((weekday + lastOfMonth.getDate()) / 7) * 7;

  const cells: DayCell[] = [];
  for (let i = 0; i < totalDays; i++) {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + i);
    const inMonth = date.getMonth() === month;
    const dueBills = inMonth
      ? bills.filter((b) => b.isActive && effectiveDueDay(b.dueDay, lastOfMonth.getDate()) === date.getDate())
      : [];
    cells.push({ date, inMonth, bills: dueBills });
  }

  const monthLabel = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(firstOfMonth);
  return { cells, monthLabel };
}

function effectiveDueDay(dueDay: number, monthLength: number): number {
  return Math.min(dueDay, monthLength);
}
