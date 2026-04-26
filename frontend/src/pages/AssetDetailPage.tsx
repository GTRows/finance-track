import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ArrowLeft, RefreshCw } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/layout/PageHeader';
import { useAsset, useAssetHistory } from '@/hooks/useAssetHistory';
import { priceApi } from '@/api/price.api';
import { formatCurrency, formatDateTime, formatShortDate } from '@/utils/formatters';
import { cn } from '@/lib/utils';

const RANGE_OPTIONS: Array<{ label: string; days: number }> = [
  { label: '7D', days: 7 },
  { label: '30D', days: 30 },
  { label: '90D', days: 90 },
];

export function AssetDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [days, setDays] = useState(30);

  const assetQuery = useAsset(id);
  const historyQuery = useAssetHistory(id, days);

  const refresh = useMutation({
    mutationFn: () => priceApi.refreshAsset(id!),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['asset', id] });
      void qc.invalidateQueries({ queryKey: ['asset-history', id] });
      void qc.invalidateQueries({ queryKey: ['assets'] });
      void qc.invalidateQueries({ queryKey: ['portfolios'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const series = useMemo(
    () =>
      (historyQuery.data ?? []).map((p) => ({
        dateLabel: formatShortDate(p.recordedAt),
        price: Number(p.price),
      })),
    [historyQuery.data],
  );

  const asset = assetQuery.data;

  if (assetQuery.isLoading) {
    return <p className="text-sm text-muted-foreground p-6">{t('common.loading')}</p>;
  }
  if (!asset) {
    return (
      <div className="space-y-4 p-6">
        <p className="text-sm text-muted-foreground">{t('common.somethingWentWrong')}</p>
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="w-4 h-4 mr-2" />
          {t('common.back')}
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-[1100px]">
      <div>
        <Link
          to="/prices"
          className="text-xs text-muted-foreground hover:text-foreground inline-flex items-center gap-1 mb-3"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          {t('prices.title')}
        </Link>
        <PageHeader
          title={`${asset.symbol} — ${asset.name}`}
          description={asset.assetType}
          actions={
            <Button
              size="sm"
              onClick={() => refresh.mutate()}
              disabled={refresh.isPending}
            >
              <RefreshCw
                className={cn('w-4 h-4 mr-2', refresh.isPending && 'animate-spin')}
              />
              {t('prices.refreshOne')}
            </Button>
          }
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <KpiCard
          label={t('prices.colPriceTry')}
          value={asset.price != null ? formatCurrency(asset.price, true, 'TRY') : '--'}
        />
        <KpiCard
          label={t('prices.colPriceUsd')}
          value={asset.priceUsd != null ? formatCurrency(asset.priceUsd, true, 'USD') : '--'}
        />
        <KpiCard
          label={t('prices.colUpdated')}
          value={asset.priceUpdatedAt ? formatDateTime(asset.priceUpdatedAt) : t('prices.never')}
          mono={false}
        />
      </div>

      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-medium">
              {t('assetDetail.priceHistory')}
            </CardTitle>
            <div className="flex items-center gap-1">
              {RANGE_OPTIONS.map((r) => (
                <button
                  key={r.days}
                  onClick={() => setDays(r.days)}
                  className={cn(
                    'text-xs px-2.5 py-1 rounded-md border transition-colors',
                    days === r.days
                      ? 'bg-primary text-primary-foreground border-primary'
                      : 'border-border text-muted-foreground hover:text-foreground hover:bg-accent',
                  )}
                >
                  {r.label}
                </button>
              ))}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {historyQuery.isLoading ? (
            <p className="text-sm text-muted-foreground py-12 text-center">
              {t('common.loading')}
            </p>
          ) : series.length < 2 ? (
            <p className="text-sm text-muted-foreground py-12 text-center">
              {t('assetDetail.historyEmpty')}
            </p>
          ) : (
            <div className="h-80">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={series} margin={{ top: 10, right: 12, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="assetPrice" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
                  <XAxis
                    dataKey="dateLabel"
                    tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                    axisLine={false}
                    tickLine={false}
                    minTickGap={24}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                    axisLine={false}
                    tickLine={false}
                    width={70}
                    tickFormatter={(v) => formatCurrency(Number(v), false, 'TRY')}
                    domain={['auto', 'auto']}
                  />
                  <Tooltip
                    contentStyle={{
                      background: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: 8,
                      fontSize: 12,
                    }}
                    formatter={(value: number) => [formatCurrency(value, true, 'TRY'), asset.symbol]}
                  />
                  <Area
                    type="monotone"
                    dataKey="price"
                    stroke="hsl(var(--primary))"
                    strokeWidth={2}
                    fill="url(#assetPrice)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function KpiCard({ label, value, mono = true }: { label: string; value: string; mono?: boolean }) {
  return (
    <Card>
      <CardContent className="py-4">
        <p className="text-[11px] uppercase tracking-wider text-muted-foreground">{label}</p>
        <p className={cn('mt-1 text-lg font-semibold', mono && 'font-mono tabular-nums')}>{value}</p>
      </CardContent>
    </Card>
  );
}
