import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { Briefcase, Plus, TrendingUp, Wallet, PieChart, Loader2 } from 'lucide-react';
import { usePortfolios } from '@/hooks/usePortfolios';
import { AddPortfolioDialog } from '@/components/portfolio/AddPortfolioDialog';
import { PortfolioListItem } from '@/components/portfolio/PortfolioListItem';

export function PortfolioPage() {
  const { t } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);
  const { data: portfolios, isLoading, isError } = usePortfolios();

  const portfolioCount = portfolios?.length ?? 0;

  return (
    <div className="space-y-6 max-w-[1200px]">
      <PageHeader
        title={t('portfolio.title')}
        description={t('portfolio.description')}
        actions={
          <Button className="cursor-pointer" onClick={() => setDialogOpen(true)}>
            <Plus className="w-4 h-4 mr-2" />
            {t('portfolio.addPortfolio')}
          </Button>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">{t('portfolio.totalValue')}</p>
                <p className="text-2xl font-semibold font-mono tracking-tight">--</p>
                <p className="text-xs text-muted-foreground">
                  {portfolioCount > 0
                    ? t('portfolio.countOther', { count: portfolioCount })
                    : t('portfolio.noHoldings')}
                </p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Wallet className="w-5 h-5 text-primary" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">{t('portfolio.totalCost')}</p>
                <p className="text-2xl font-semibold font-mono tracking-tight">--</p>
                <p className="text-xs text-muted-foreground">{t('portfolio.totalCostHint')}</p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <PieChart className="w-5 h-5 text-primary" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">{t('portfolio.unrealizedPnl')}</p>
                <p className="text-2xl font-semibold font-mono tracking-tight">--</p>
                <p className="text-xs text-muted-foreground">{t('portfolio.pnlHint')}</p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <TrendingUp className="w-5 h-5 text-primary" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="pb-2 flex-row items-center justify-between">
          <CardTitle className="text-sm font-medium">
            {t('portfolio.yourPortfolios')}{portfolioCount > 0 && ` (${portfolioCount})`}
          </CardTitle>
        </CardHeader>
        <CardContent className="px-0">
          {isLoading && (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
            </div>
          )}

          {isError && (
            <div className="px-6 py-12 text-center">
              <p className="text-sm text-destructive">{t('portfolio.failedToLoad')}</p>
            </div>
          )}

          {!isLoading && !isError && portfolioCount === 0 && (
            <EmptyState
              icon={Briefcase}
              title={t('portfolio.noPortfoliosTitle')}
              description={t('portfolio.noPortfoliosDesc')}
              action={
                <Button className="cursor-pointer" onClick={() => setDialogOpen(true)}>
                  <Plus className="w-4 h-4 mr-2" />
                  {t('portfolio.createPortfolio')}
                </Button>
              }
            />
          )}

          {!isLoading && !isError && portfolioCount > 0 && (
            <div>
              {portfolios!.map((p) => (
                <PortfolioListItem key={p.id} portfolio={p} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <AddPortfolioDialog open={dialogOpen} onOpenChange={setDialogOpen} />
    </div>
  );
}
