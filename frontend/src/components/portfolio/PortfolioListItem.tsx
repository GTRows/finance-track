import { MoreVertical, Trash2, Loader2, ChevronRight } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useDeletePortfolio } from '@/hooks/usePortfolios';
import type { Portfolio } from '@/types/portfolio.types';
import { cn } from '@/lib/utils';
import { getPortfolioTypeMeta } from './portfolio-types';

interface PortfolioListItemProps {
  portfolio: Portfolio;
}

export function PortfolioListItem({ portfolio }: PortfolioListItemProps) {
  const { t } = useTranslation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const deleteMutation = useDeletePortfolio();

  const meta = getPortfolioTypeMeta(portfolio.type);
  const Icon = meta.icon;

  const handleDelete = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!confirming) {
      setConfirming(true);
      return;
    }
    try {
      await deleteMutation.mutateAsync(portfolio.id);
    } finally {
      setConfirming(false);
      setMenuOpen(false);
    }
  };

  const stopNav = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
  };

  return (
    <Link
      to={`/portfolio/${portfolio.id}`}
      className="group flex items-center gap-4 px-5 py-4 border-b last:border-b-0 hover:bg-accent/30 transition-colors"
    >
      <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
        <Icon className="w-5 h-5 text-primary" />
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-medium truncate">{portfolio.name}</h3>
          <span
            className={cn(
              'text-[10px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded whitespace-nowrap',
              meta.badgeClass
            )}
          >
            {t(meta.labelKey)}
          </span>
        </div>
        {portfolio.description && (
          <p className="text-xs text-muted-foreground mt-0.5 truncate">{portfolio.description}</p>
        )}
      </div>

      <div className="text-right hidden sm:block">
        <p className="text-sm font-mono">--</p>
        <p className="text-xs text-muted-foreground">{t('portfolio.noHoldingsShort')}</p>
      </div>

      <ChevronRight className="w-4 h-4 text-muted-foreground/50 group-hover:text-muted-foreground transition-colors hidden sm:block" />

      <div className="relative" onClick={stopNav}>
        <button
          type="button"
          onClick={(e) => {
            stopNav(e);
            setMenuOpen(!menuOpen);
          }}
          className="w-8 h-8 rounded-md flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer opacity-0 group-hover:opacity-100 focus:opacity-100"
        >
          <MoreVertical className="w-4 h-4" />
        </button>
        {menuOpen && (
          <>
            <div
              className="fixed inset-0 z-10"
              onClick={(e) => {
                stopNav(e);
                setMenuOpen(false);
                setConfirming(false);
              }}
            />
            <div className="absolute right-0 top-9 z-20 min-w-[180px] rounded-md border bg-popover shadow-lg p-1">
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
                className="w-full flex items-center gap-2 px-2.5 py-2 text-sm text-destructive hover:bg-destructive/10 rounded cursor-pointer transition-colors disabled:opacity-50"
              >
                {deleteMutation.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Trash2 className="w-4 h-4" />
                )}
                {confirming ? t('common.confirmAgain') : t('portfolio.deletePortfolio')}
              </button>
            </div>
          </>
        )}
      </div>
    </Link>
  );
}
