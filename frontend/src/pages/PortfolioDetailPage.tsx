import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  ArrowLeft,
  Plus,
  Loader2,
  Wallet,
  TrendingUp,
  PieChart,
  Coins,
  RefreshCw,
  Download,
} from 'lucide-react';
import { reportApi } from '@/api/report.api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { EmptyState } from '@/components/layout/EmptyState';
import { usePortfolio } from '@/hooks/usePortfolios';
import { useHoldings } from '@/hooks/useHoldings';
import { useSnapshots } from '@/hooks/useSnapshots';
import { useRefreshPrices } from '@/hooks/useRefreshPrices';
import { getPortfolioTypeMeta } from '@/components/portfolio/portfolio-types';
import { HoldingsTable } from '@/components/portfolio/HoldingsTable';
import { AddHoldingDialog } from '@/components/portfolio/AddHoldingDialog';
import { TransactionLog } from '@/components/portfolio/TransactionLog';
import { AllocationChart } from '@/components/portfolio/AllocationChart';
import { PortfolioHistoryChart } from '@/components/portfolio/PortfolioHistoryChart';
import { formatTRY, formatPercent } from '@/utils/formatters';
import { cn } from '@/lib/utils';

export function PortfolioDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const [dialogOpen, setDialogOpen] = useState(false);

  const portfolioQuery = usePortfolio(id);
  const holdingsQuery = useHoldings(id);
  const snapshotsQuery = useSnapshots(id);
  const refreshPrices = useRefreshPrices();
  const [downloading, setDownloading] = useState(false);

  const handleDownloadPdf = async () => {
    if (!portfolioQuery.data) return;
    try {
      setDownloading(true);
      await reportApi.downloadPortfolioPdf(portfolioQuery.data.id, portfolioQuery.data.name);
    } finally {
      setDownloading(false);
    }
  };

  const stats = useMemo(() => {
    const holdings = holdingsQuery.data ?? [];
    const value = holdings.reduce((acc, h) => acc + (h.currentValueTry ?? 0), 0);
    const cost = holdings.reduce((acc, h) => acc + (h.costBasisTry ?? 0), 0);
    const pnl = value - cost;
    const pnlPercent = cost > 0 ? pnl / cost : null;
    return { value, cost, pnl, pnlPercent, count: holdings.length };
  }, [holdingsQuery.data]);

  const lastPriceUpdate = useMemo(() => {
    const holdings = holdingsQuery.data ?? [];
    const timestamps = holdings
      .map((h) => h.priceUpdatedAt)
      .filter((t): t is string => t != null);
    if (timestamps.length === 0) return null;
    return timestamps.sort().reverse()[0];
  }, [holdingsQuery.data]);

  if (portfolioQuery.isLoading) {
    return (
      <div className="flex items-center justify-center py-24">
        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (portfolioQuery.isError || !portfolioQuery.data) {
    return (
      <div className="max-w-[1200px] space-y-4">
        <Link
          to="/portfolio"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          {t('portfolio.backToPortfolios')}
        </Link>
        <Card>
          <CardContent className="px-6 py-16 text-center">
            <p className="text-sm text-destructive">{t('portfolio.notFound')}</p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const portfolio = portfolioQuery.data;
  const meta = getPortfolioTypeMeta(portfolio.type);
  const Icon = meta.icon;
  const pnlPositive = stats.pnl >= 0;
  const holdings = holdingsQuery.data ?? [];

  return (
    <div className="max-w-[1200px] space-y-6">
      <div>
        <Link
          to="/portfolio"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors mb-4"
        >
          <ArrowLeft className="w-4 h-4" />
          {t('portfolio.backToPortfolios')}
        </Link>

        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-4 min-w-0">
            <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
              <Icon className="w-6 h-6 text-primary" />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-2xl font-semibold tracking-tight truncate">{portfolio.name}</h1>
                <span
                  className={cn(
                    'text-[10px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded',
                    meta.badgeClass
                  )}
                >
                  {t(meta.labelKey)}
                </span>
              </div>
              {portfolio.description && (
                <p className="text-sm text-muted-foreground mt-1">{portfolio.description}</p>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <button
              type="button"
              onClick={() => refreshPrices.mutate()}
              disabled={refreshPrices.isPending}
              title={t('holdings.forceRefresh')}
              className="w-9 h-9 rounded-md border border-input flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer disabled:opacity-50"
            >
              <RefreshCw
                className={cn('w-4 h-4', refreshPrices.isPending && 'animate-spin')}
              />
            </button>
            <button
              type="button"
              onClick={handleDownloadPdf}
              disabled={downloading || holdings.length === 0}
              title={t('reports.downloadPdf')}
              className="w-9 h-9 rounded-md border border-input flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer disabled:opacity-50"
            >
              {downloading ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Download className="w-4 h-4" />
              )}
            </button>
            <Button className="cursor-pointer" onClick={() => setDialogOpen(true)}>
              <Plus className="w-4 h-4 mr-2" />
              {t('holdings.addHolding')}
            </Button>
          </div>
        </div>
        <div className="mt-3 pl-16 flex items-center gap-2 text-xs text-muted-foreground">
          <span className="live-indicator">{t('holdings.live')}</span>
          {lastPriceUpdate && (
            <span>{t('holdings.updatedAgo', { time: formatRelativeTime(lastPriceUpdate, t) })}</span>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          icon={Wallet}
          label={t('holdings.currentValue')}
          value={stats.count > 0 ? formatTRY(stats.value) : '--'}
          hint={t('holdings.holdingOther', { count: stats.count })}
        />
        <StatCard
          icon={PieChart}
          label={t('holdings.costBasis')}
          value={stats.count > 0 ? formatTRY(stats.cost) : '--'}
          hint={t('holdings.costBasisHint')}
        />
        <StatCard
          icon={TrendingUp}
          label={t('holdings.pnl')}
          value={stats.count > 0 ? formatTRY(stats.pnl, true) : '--'}
          hint={
            stats.pnlPercent != null
              ? formatPercent(stats.pnlPercent)
              : t('holdings.pnlEmpty')
          }
          tone={stats.count > 0 ? (pnlPositive ? 'positive' : 'negative') : undefined}
        />
        <StatCard
          icon={Coins}
          label={t('holdings.assets')}
          value={String(stats.count)}
          hint={stats.count > 0 ? t('holdings.distinctPositions') : t('holdings.addFirstHint')}
        />
      </div>

      {holdings.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] gap-4">
          <Card>
            <CardHeader className="pb-4">
              <CardTitle className="text-sm font-medium">{t('holdings.allocation')}</CardTitle>
              <CardDescription className="text-xs">{t('holdings.allocationHint')}</CardDescription>
            </CardHeader>
            <CardContent>
              <AllocationChart holdings={holdings} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-4">
              <CardTitle className="text-sm font-medium">{t('holdings.history')}</CardTitle>
              <CardDescription className="text-xs">{t('holdings.historyHint')}</CardDescription>
            </CardHeader>
            <CardContent>
              <PortfolioHistoryChart snapshots={snapshotsQuery.data ?? []} />
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardContent className="p-0">
          {holdingsQuery.isLoading && (
            <div className="flex items-center justify-center py-16">
              <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
            </div>
          )}

          {holdingsQuery.isError && (
            <div className="px-6 py-16 text-center">
              <p className="text-sm text-destructive">{t('holdings.failedToLoad')}</p>
            </div>
          )}

          {!holdingsQuery.isLoading && !holdingsQuery.isError && holdings.length === 0 && (
            <EmptyState
              icon={Coins}
              title={t('holdings.emptyTitle')}
              description={t('holdings.emptyDesc')}
              action={
                <Button className="cursor-pointer" onClick={() => setDialogOpen(true)}>
                  <Plus className="w-4 h-4 mr-2" />
                  {t('holdings.addHolding')}
                </Button>
              }
            />
          )}

          {!holdingsQuery.isLoading && !holdingsQuery.isError && holdings.length > 0 && (
            <HoldingsTable portfolioId={portfolio.id} holdings={holdings} />
          )}
        </CardContent>
      </Card>

      <TransactionLog portfolioId={portfolio.id} />

      <AddHoldingDialog
        open={dialogOpen}
        portfolioId={portfolio.id}
        onOpenChange={setDialogOpen}
      />
    </div>
  );
}

interface StatCardProps {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  hint: string;
  tone?: 'positive' | 'negative';
}

/** Formats an ISO timestamp as a short, localized relative string. */
function formatRelativeTime(iso: string, t: (key: string, opts?: Record<string, unknown>) => string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const diffSec = Math.round(diffMs / 1000);
  if (diffSec < 60) return t('holdings.timeJustNow');
  const diffMin = Math.round(diffSec / 60);
  if (diffMin < 60) return t('holdings.timeMin', { count: diffMin });
  const diffHour = Math.round(diffMin / 60);
  if (diffHour < 24) return t('holdings.timeHour', { count: diffHour });
  const diffDay = Math.round(diffHour / 24);
  return diffDay === 1
    ? t('holdings.timeDayOne', { count: diffDay })
    : t('holdings.timeDayOther', { count: diffDay });
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
            <p
              className={cn(
                'text-xs',
                tone === 'positive'
                  ? 'text-positive/80'
                  : tone === 'negative'
                    ? 'text-negative/80'
                    : 'text-muted-foreground'
              )}
            >
              {hint}
            </p>
          </div>
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
            <Icon className="w-5 h-5 text-primary" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
