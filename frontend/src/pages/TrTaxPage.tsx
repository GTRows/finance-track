import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  ArrowLeft,
  Loader2,
  Receipt,
  Landmark,
  Coins,
  Gauge,
  ShieldCheck,
  ShieldAlert,
  ShieldX,
  ShieldQuestion,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { useTrTaxReport } from '@/hooks/useTrTaxReport';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import type { TrTaxStatus } from '@/types/tax.types';

const STATUS_VISUALS: Record<
  TrTaxStatus,
  { icon: typeof ShieldCheck; tone: 'positive' | 'negative' | 'neutral' | 'warning' }
> = {
  under: { icon: ShieldCheck, tone: 'positive' },
  approaching: { icon: ShieldAlert, tone: 'warning' },
  over: { icon: ShieldX, tone: 'negative' },
  unknown: { icon: ShieldQuestion, tone: 'neutral' },
};

function recentYears(): number[] {
  const now = new Date().getFullYear();
  return [now, now - 1, now - 2, now - 3, now - 4];
}

export function TrTaxPage() {
  const { t } = useTranslation();
  const years = useMemo(() => recentYears(), []);
  const [selectedYear, setSelectedYear] = useState<number>(years[0]);
  const query = useTrTaxReport(selectedYear);

  const report = query.data;
  const isLoading = query.isLoading;

  return (
    <div className="space-y-6 max-w-[1200px]">
      <PageHeader
        title={t('taxTr.title')}
        description={t('taxTr.description')}
        actions={
          <Link
            to="/analytics"
            className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            {t('taxTr.backToAnalytics')}
          </Link>
        }
      />

      <div className="flex flex-wrap items-center gap-1.5">
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
      ) : !report ? (
        <Card>
          <CardContent className="p-0">
            <EmptyState
              icon={Coins}
              title={t('taxTr.emptyTitle')}
              description={t('taxTr.emptyDesc')}
            />
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatusCard status={report.capitalGains.status} t={t} />
            <StatCard
              icon={Receipt}
              label={t('taxTr.realizedYtd')}
              value={formatTRY(report.capitalGains.realizedTry)}
              hint={t('taxTr.realizedYtdHint')}
              tone={report.capitalGains.realizedTry >= 0 ? 'positive' : 'negative'}
            />
            <StatCard
              icon={Gauge}
              label={t('taxTr.threshold')}
              value={
                report.capitalGains.thresholdTry != null
                  ? formatTRY(report.capitalGains.thresholdTry)
                  : '—'
              }
              hint={t('taxTr.thresholdHint')}
            />
            <StatCard
              icon={Landmark}
              label={t('taxTr.headroom')}
              value={
                report.capitalGains.headroomTry != null
                  ? formatTRY(report.capitalGains.headroomTry)
                  : '—'
              }
              hint={t('taxTr.headroomHint')}
              tone={
                report.capitalGains.headroomTry == null
                  ? undefined
                  : report.capitalGains.headroomTry >= 0
                    ? 'positive'
                    : 'negative'
              }
            />
          </div>

          {report.warnings.length > 0 && (
            <Card>
              <CardContent className="p-4 space-y-2">
                {report.warnings.map((warning) => {
                  const [token, suffix] = warning.split(':');
                  const labelKey = `taxTr.warnings.${token}` as const;
                  return (
                    <div
                      key={warning}
                      className="text-xs text-muted-foreground flex items-start gap-2"
                    >
                      <ShieldAlert className="w-3.5 h-3.5 text-amber-500 mt-0.5 flex-shrink-0" />
                      <span>
                        {t(labelKey, { defaultValue: token })}
                        {suffix ? ` (${suffix})` : null}
                      </span>
                    </div>
                  );
                })}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium">
                {t('taxTr.stoppage.sectionTitle')}
              </CardTitle>
              <CardDescription className="text-xs">
                {t('taxTr.stoppage.sectionDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b border-border text-xs uppercase tracking-wider text-muted-foreground">
                    <tr>
                      <th className="text-left font-medium py-2.5 px-4">
                        {t('taxTr.stoppage.columns.asset')}
                      </th>
                      <th className="text-right font-medium py-2.5 px-4">
                        {t('taxTr.stoppage.columns.gross')}
                      </th>
                      <th className="text-right font-medium py-2.5 px-4">
                        {t('taxTr.stoppage.columns.withholding')}
                      </th>
                      <th className="text-right font-medium py-2.5 px-4">
                        {t('taxTr.stoppage.columns.net')}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.dividendStoppage.byAsset.length === 0 ? (
                      <tr>
                        <td
                          colSpan={4}
                          className="py-8 text-center text-xs text-muted-foreground"
                        >
                          {t('taxTr.stoppage.emptyRow')}
                        </td>
                      </tr>
                    ) : (
                      report.dividendStoppage.byAsset.map((row) => (
                        <tr
                          key={row.assetId}
                          className="border-b border-border/60 last:border-0 hover:bg-accent/30 transition-colors"
                        >
                          <td className="py-2.5 px-4">
                            <div className="flex flex-col">
                              <span className="font-medium">
                                {row.assetSymbol ?? t('taxTr.stoppage.unknownAsset')}
                              </span>
                              {row.assetName && (
                                <span className="text-xs text-muted-foreground truncate max-w-[220px]">
                                  {row.assetName}
                                </span>
                              )}
                            </div>
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums">
                            {formatTRY(row.grossTry)}
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums text-muted-foreground">
                            {formatTRY(row.withholdingTry)}
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono tabular-nums">
                            {formatTRY(row.netTry)}
                          </td>
                        </tr>
                      ))
                    )}
                    <tr className="border-t border-border/80 bg-accent/10">
                      <td className="py-2.5 px-4 text-xs uppercase tracking-wider font-medium text-muted-foreground">
                        {t('taxTr.stoppage.total')}
                      </td>
                      <td className="py-2.5 px-4 text-right font-mono tabular-nums font-medium">
                        {formatTRY(report.dividendStoppage.totalGrossTry)}
                      </td>
                      <td className="py-2.5 px-4 text-right font-mono tabular-nums font-medium text-muted-foreground">
                        {formatTRY(report.dividendStoppage.totalWithholdingTry)}
                      </td>
                      <td className="py-2.5 px-4 text-right font-mono tabular-nums font-medium">
                        {formatTRY(report.dividendStoppage.totalNetTry)}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium">{t('taxTr.source')}</CardTitle>
              <CardDescription className="text-xs">
                {report.parameters?.source ?? t('taxTr.parametersMissing')}
              </CardDescription>
            </CardHeader>
            {report.parameters?.notes && (
              <CardContent className="pt-0">
                <p className="text-xs text-muted-foreground">{report.parameters.notes}</p>
              </CardContent>
            )}
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
          : 'border-border text-muted-foreground hover:text-foreground hover:border-border/80',
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
                tone === 'negative' && 'text-negative',
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

interface StatusCardProps {
  status: TrTaxStatus;
  t: (key: string) => string;
}

function StatusCard({ status, t }: StatusCardProps) {
  const visual = STATUS_VISUALS[status];
  const Icon = visual.icon;
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <div className="space-y-2 min-w-0">
            <p className="text-sm text-muted-foreground font-medium">{t('taxTr.statusLabel')}</p>
            <p
              className={cn(
                'text-2xl font-semibold tracking-tight truncate',
                visual.tone === 'positive' && 'text-positive',
                visual.tone === 'negative' && 'text-negative',
                visual.tone === 'warning' && 'text-amber-500',
                visual.tone === 'neutral' && 'text-muted-foreground',
              )}
            >
              {t(`taxTr.status.${status}`)}
            </p>
            <p className="text-xs text-muted-foreground">{t(`taxTr.statusHint.${status}`)}</p>
          </div>
          <div
            className={cn(
              'w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0',
              visual.tone === 'positive' && 'bg-emerald-500/10 text-emerald-500',
              visual.tone === 'negative' && 'bg-rose-500/10 text-rose-500',
              visual.tone === 'warning' && 'bg-amber-500/10 text-amber-500',
              visual.tone === 'neutral' && 'bg-muted text-muted-foreground',
            )}
          >
            <Icon className="w-5 h-5" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
