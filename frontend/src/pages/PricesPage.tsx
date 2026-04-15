import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAssets } from '@/hooks/useAssets';
import { priceApi } from '@/api/price.api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/layout/PageHeader';
import { AddFundDialog } from '@/components/prices/AddFundDialog';
import { formatCurrency, formatDateTime } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import { RefreshCw, Search } from 'lucide-react';
import type { Asset, AssetType } from '@/types/portfolio.types';

const TYPE_FILTERS: Array<AssetType | 'ALL'> = ['ALL', 'CRYPTO', 'FUND', 'CURRENCY', 'GOLD', 'STOCK'];

export function PricesPage() {
  const { t } = useTranslation();
  const { data: assets = [], isLoading } = useAssets();
  const qc = useQueryClient();
  const navigate = useNavigate();

  const [filter, setFilter] = useState<AssetType | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);

  const refreshOne = useMutation({
    mutationFn: (assetId: string) => priceApi.refreshAsset(assetId),
    onMutate: (assetId) => setBusyId(assetId),
    onSettled: () => setBusyId(null),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['assets'] });
      qc.invalidateQueries({ queryKey: ['portfolios'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const refreshAll = useMutation({
    mutationFn: () => priceApi.refresh(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['assets'] });
      qc.invalidateQueries({ queryKey: ['portfolios'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return assets.filter((a) => {
      if (filter !== 'ALL' && a.assetType !== filter) return false;
      if (!q) return true;
      return a.symbol.toLowerCase().includes(q) || a.name.toLowerCase().includes(q);
    });
  }, [assets, filter, search]);

  return (
    <div className="space-y-6 max-w-[1200px]">
      <PageHeader
        title={t('prices.title')}
        description={t('prices.description')}
        actions={
          <>
            <AddFundDialog />
            <Button
              size="sm"
              onClick={() => refreshAll.mutate()}
              disabled={refreshAll.isPending}
            >
              <RefreshCw className={cn('w-4 h-4 mr-2', refreshAll.isPending && 'animate-spin')} />
              {t('prices.refreshAll')}
            </Button>
          </>
        }
      />

      <Card>
        <CardHeader className="pb-3">
          <div className="flex flex-col md:flex-row md:items-center gap-3 justify-between">
            <CardTitle className="text-sm font-medium">{t('prices.allAssets')}</CardTitle>
            <div className="flex flex-wrap items-center gap-2">
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
                <Input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder={t('prices.searchPlaceholder')}
                  className="pl-8 h-8 w-48"
                />
              </div>
              <div className="flex items-center gap-1">
                {TYPE_FILTERS.map((f) => (
                  <button
                    key={f}
                    onClick={() => setFilter(f)}
                    className={cn(
                      'text-xs px-2.5 py-1 rounded-md border transition-colors',
                      filter === f
                        ? 'bg-primary text-primary-foreground border-primary'
                        : 'border-border text-muted-foreground hover:text-foreground hover:bg-accent'
                    )}
                  >
                    {t(`prices.filter.${f}`)}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground py-6 text-center">{t('common.loading')}</p>
          ) : filtered.length === 0 ? (
            <p className="text-sm text-muted-foreground py-6 text-center">{t('common.noData')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-[11px] uppercase tracking-wider text-muted-foreground border-b border-border/50">
                    <th className="text-left font-medium py-2 px-2">{t('prices.colSymbol')}</th>
                    <th className="text-left font-medium py-2 px-2">{t('prices.colName')}</th>
                    <th className="text-left font-medium py-2 px-2">{t('prices.colType')}</th>
                    <th className="text-right font-medium py-2 px-2">{t('prices.colPriceTry')}</th>
                    <th className="text-right font-medium py-2 px-2">{t('prices.colPriceUsd')}</th>
                    <th className="text-left font-medium py-2 px-2">{t('prices.colUpdated')}</th>
                    <th className="text-right font-medium py-2 px-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((a) => (
                    <PriceRow
                      key={a.id}
                      asset={a}
                      onRefresh={() => refreshOne.mutate(a.id)}
                      onOpen={() => navigate(`/prices/${a.id}`)}
                      busy={busyId === a.id}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function PriceRow({
  asset,
  onRefresh,
  onOpen,
  busy,
}: {
  asset: Asset;
  onRefresh: () => void;
  onOpen: () => void;
  busy: boolean;
}) {
  const { t } = useTranslation();
  return (
    <tr
      className="border-b border-border/30 hover:bg-accent/30 transition-colors cursor-pointer"
      onClick={onOpen}
    >
      <td className="py-2.5 px-2 font-mono font-medium">
        <Link
          to={`/prices/${asset.id}`}
          className="hover:underline"
          onClick={(e) => e.stopPropagation()}
        >
          {asset.symbol}
        </Link>
      </td>
      <td className="py-2.5 px-2 text-muted-foreground truncate max-w-[240px]">{asset.name}</td>
      <td className="py-2.5 px-2">
        <span className="text-[11px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-muted text-muted-foreground">
          {asset.assetType}
        </span>
      </td>
      <td className="py-2.5 px-2 text-right font-mono tabular-nums">
        {asset.price != null ? formatCurrency(asset.price, true, 'TRY') : '--'}
      </td>
      <td className="py-2.5 px-2 text-right font-mono tabular-nums text-muted-foreground">
        {asset.priceUsd != null ? formatCurrency(asset.priceUsd, true, 'USD') : '--'}
      </td>
      <td className="py-2.5 px-2 text-[11px] text-muted-foreground">
        {asset.priceUpdatedAt ? formatDateTime(asset.priceUpdatedAt) : t('prices.never')}
      </td>
      <td className="py-2.5 px-2 text-right" onClick={(e) => e.stopPropagation()}>
        <Button
          size="sm"
          variant="ghost"
          onClick={onRefresh}
          disabled={busy}
          title={t('prices.refreshOne')}
        >
          <RefreshCw className={cn('w-3.5 h-3.5', busy && 'animate-spin')} />
        </Button>
      </td>
    </tr>
  );
}
