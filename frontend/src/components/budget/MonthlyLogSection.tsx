import { useTranslation } from 'react-i18next';
import { Camera, Loader2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useMonthlySummaries, useCaptureSnapshot } from '@/hooks/useBudget';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';

interface MonthlyLogSectionProps {
  currentMonth: string;
}

export function MonthlyLogSection({ currentMonth }: MonthlyLogSectionProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage === 'tr' ? 'tr-TR' : 'en-US';
  const summaries = useMonthlySummaries();
  const capture = useCaptureSnapshot(currentMonth);

  const formatPeriod = (period: string) => {
    const [y, m] = period.split('-').map(Number);
    return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' })
      .format(new Date(y, m - 1));
  };

  const rows = summaries.data ?? [];

  return (
    <Card>
      <CardHeader className="pb-3 flex flex-row items-center justify-between space-y-0">
        <div>
          <CardTitle className="text-sm font-medium">{t('budget.monthlyLog')}</CardTitle>
          <p className="text-xs text-muted-foreground mt-1">{t('budget.monthlyLogHint')}</p>
        </div>
        <Button
          variant="outline"
          size="sm"
          className="cursor-pointer"
          onClick={() => capture.mutate()}
          disabled={capture.isPending}
        >
          {capture.isPending ? (
            <Loader2 className="w-3.5 h-3.5 mr-2 animate-spin" />
          ) : (
            <Camera className="w-3.5 h-3.5 mr-2" />
          )}
          {t('budget.captureSnapshot')}
        </Button>
      </CardHeader>
      <CardContent className="px-0">
        {rows.length === 0 ? (
          <div className="px-6 py-10 text-center">
            <p className="text-sm text-muted-foreground">{t('budget.noSnapshots')}</p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            <div className="grid grid-cols-[1fr_repeat(4,minmax(0,1fr))] gap-3 px-6 py-2 text-[11px] uppercase tracking-wider text-muted-foreground">
              <span>{t('budget.period')}</span>
              <span className="text-right">{t('budget.income')}</span>
              <span className="text-right">{t('budget.expenses')}</span>
              <span className="text-right">{t('budget.net')}</span>
              <span className="text-right">{t('budget.savingsRate')}</span>
            </div>
            {rows.map((row) => (
              <div
                key={row.period}
                className="grid grid-cols-[1fr_repeat(4,minmax(0,1fr))] gap-3 px-6 py-3 text-sm items-center hover:bg-accent/30 transition-colors"
              >
                <span className="font-medium capitalize">{formatPeriod(row.period)}</span>
                <span className="text-right font-mono tabular-nums text-emerald-400">
                  {formatTRY(row.totalIncome)}
                </span>
                <span className="text-right font-mono tabular-nums text-red-400">
                  {formatTRY(row.totalExpense)}
                </span>
                <span
                  className={cn(
                    'text-right font-mono tabular-nums',
                    row.net >= 0 ? 'text-emerald-400' : 'text-red-400'
                  )}
                >
                  {formatTRY(row.net)}
                </span>
                <span className="text-right font-mono tabular-nums text-muted-foreground">
                  {row.savingsRate != null ? `%${row.savingsRate.toFixed(1)}` : '--'}
                </span>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
