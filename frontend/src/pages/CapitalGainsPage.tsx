import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowUpRight,
  ArrowDownRight,
  Loader2,
  Percent,
  Coins,
  Receipt,
  Landmark,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { useCapitalGains } from '@/hooks/useCapitalGains';
import { formatTRY, formatShortDate } from '@/utils/formatters';
import { cn } from '@/lib/utils';

export function CapitalGainsPage() {
  const { t } = useTranslation();
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const allYearsQuery = useCapitalGains(null);
  const activeQuery = useCapitalGains(selectedYear);

  const years = useMemo(
    () => (allYearsQuery.data?.byYear ?? []).map((row) => row.year).sort((a, b) => b - a),
    [allYearsQuery.data]
  );

  const report = activeQuery.data;
  const isLoading = allYearsQuery.isLoading || activeQuery.isLoading;

  const dividendIncluded = report?.dividendsNetTry ?? 0;
  const totalTaxableBase = (report?.realizedGain ?? 0) + dividendIncluded;

  return (
    <div className="space-y-6 max-w-[1200px]">
      <PageHeader
        title={t('capitalGains.title')}
        description={t('capitalGains.description')}
        actions={
          <Link
            to="/analytics"
            className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            {t('capitalGains.backToAnalytics')}
          </Link>
        }
      />

      <div className="flex flex-wrap items-center gap-1.5">
        <YearPill
          active={selectedYear === null}
          label={t('capitalGains.allYears')}
          onClick={() => setSelectedYear(null)}
        />
        {years.map((year) => (
          <YearPill
            key={year}
            active={selectedYear === year}
            label={String(year)}
            onClick={() => setSelectedYear(year)}
          />
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-24">
          <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
        </div>
      ) : !report || (report.byYear.length === 0 && report.events.length === 0) ? (
        <Card>
          <CardContent className="p-0">
            <EmptyState
              icon={Coins}
              title={t('capitalGains.emptyTitle')}
              description={t('capitalGains.emptyDesc')}
            />
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              icon={ArrowUpRight}
              label={t('capitalGains.realizedGain')}
              value={formatTRY(report.realizedGain)}
              hint={t('capitalGains.realizedGainHint')}
              tone={report.realizedGain >= 0 ? 'positive' : 'negative'}
            />
            <StatCard
              icon={Receipt}
              label={t('capitalGains.totalProceeds')}
              value={formatTRY(report.totalProceeds)}
              hint={t('capitalGains.totalProceedsHint', { count: report.events.length })}
            />
            <StatCard
              icon={Landmark}
              label={t('capitalGains.dividendsNet')}
              value={formatTRY(dividendIncluded)}
              hint={t('capitalGains.dividendsNetHint')}
            />
            <StatCard
              icon={Percent}
              label={t('capitalGains.taxableBase')}
              value={formatTRY(totalTaxableBase)}
              hint={t('capitalGains.taxableBaseHint')}
              tone={totalTaxableBase >= 0 ? 'positive' : 'negative'}
            />
          </div>

          {report.byYear.length > 0 && (
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-sm font-medium">
                  {t('capitalGains.byYearTitle')}
                </CardTitle>
                <CardDescription className="text-xs">
                  {t('capitalGains.byYearHint')}
                </CardDescription>
              </CardHeader>
              <CardContent className="p-0">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="border-b border-border text-xs uppercase tracking-wider text-muted-foreground">
                      <tr>
                        <th className="text-left font-medium py-2.5 px-4">
                          {t('capitalGains.columns.year')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.proceeds')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.costBasis')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.fees')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.realizedGain')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.dividends')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.events')}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.byYear.map((row) => (
                        <tr
                          key={row.year}
                          className="border-b border-border/60 last:border-0 hover:bg-accent/30 transition-colors cursor-pointer"
                          onClick={() => setSelectedYear(row.year)}
                        >
                          <td className="py-2.5 px-4 font-medium tabular-nums">{row.year}</td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums">
                            {formatTRY(row.proceeds)}
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums text-muted-foreground">
                            {formatTRY(row.costBasis)}
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums text-muted-foreground">
                            {formatTRY(row.fees)}
                          </td>
                          <td
                            className={cn(
                              'py-2.5 px-4 text-right font-mono tabular-nums font-medium',
                              row.realizedGain >= 0 ? 'text-positive' : 'text-negative'
                            )}
                          >
                            {formatTRY(row.realizedGain)}
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums text-muted-foreground">
                            {formatTRY(row.dividendsNetTry)}
                          </td>
                          <td className="py-2.5 px-4 text-right tabular-nums text-muted-foreground">
                            {row.eventCount}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium">
                {t('capitalGains.eventsTitle')}
              </CardTitle>
              <CardDescription className="text-xs">
                {selectedYear != null
                  ? t('capitalGains.eventsHintYear', { year: selectedYear })
                  : t('capitalGains.eventsHintAll')}
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              {report.events.length === 0 ? (
                <EmptyState
                  icon={Receipt}
                  title={t('capitalGains.noEventsTitle')}
                  description={t('capitalGains.noEventsDesc')}
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="border-b border-border text-xs uppercase tracking-wider text-muted-foreground">
                      <tr>
                        <th className="text-left font-medium py-2.5 px-4">
                          {t('capitalGains.columns.date')}
                        </th>
                        <th className="text-left font-medium py-2.5 px-4">
                          {t('capitalGains.columns.asset')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.quantity')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.price')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.proceeds')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.costBasis')}
                        </th>
                        <th className="text-right font-medium py-2.5 px-4">
                          {t('capitalGains.columns.gain')}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.events.map((event) => (
                        <tr
                          key={event.transactionId}
                          className="border-b border-border/60 last:border-0 hover:bg-accent/30 transition-colors"
                        >
                          <td className="py-2.5 px-4 tabular-nums text-muted-foreground">
                            {formatShortDate(event.txnDate)}
                          </td>
                          <td className="py-2.5 px-4">
                            <div className="flex flex-col">
                              <span className="font-medium">
                                {event.assetSymbol ?? t('capitalGains.unknownAsset')}
                              </span>
                              {event.assetName && (
                                <span className="text-xs text-muted-foreground truncate max-w-[220px]">
                                  {event.assetName}
                                </span>
                              )}
                            </div>
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums">
                            {event.quantity}
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums text-muted-foreground">
                            {formatTRY(event.pricePerUnit)}
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums">
                            {formatTRY(event.proceeds)}
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums text-muted-foreground">
                            {formatTRY(event.costBasis)}
                          </td>
                          <td
                            className={cn(
                              'py-2.5 px-4 text-right font-mono tabular-nums font-medium',
                              event.realizedGain >= 0 ? 'text-positive' : 'text-negative'
                            )}
                          >
                            <span className="inline-flex items-center gap-1">
                              {event.realizedGain >= 0 ? (
                                <ArrowUpRight className="w-3 h-3" />
                              ) : (
                                <ArrowDownRight className="w-3 h-3" />
                              )}
                              {formatTRY(event.realizedGain)}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

interface YearPillProps {
  active: boolean;
  label: string;
  onClick: () => void;
}

function YearPill({ active, label, onClick }: YearPillProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'h-8 px-3 rounded-md border text-xs font-medium tabular-nums transition-colors cursor-pointer',
        active
          ? 'bg-primary/10 border-primary/40 text-primary'
          : 'border-border text-muted-foreground hover:text-foreground hover:border-border/80'
      )}
    >
      {label}
    </button>
  );
}

interface StatCardProps {
  icon: React.ElementType;
  label: string;
  value: string;
  hint: string;
  tone?: 'positive' | 'negative';
}

function StatCard({ icon: Icon, label, value, hint, tone }: StatCardProps) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <div className="space-y-2 min-w-0">
            <p className="text-sm text-muted-foreground font-medium">{label}</p>
            <p
              className={cn(
                'text-2xl font-semibold font-mono tracking-tight tabular-nums truncate',
                tone === 'positive' && 'text-positive',
                tone === 'negative' && 'text-negative'
              )}
            >
              {value}
            </p>
            <p className="text-xs text-muted-foreground">{hint}</p>
          </div>
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
            <Icon className="w-5 h-5 text-primary" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
