import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus, Loader2, Check } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { assetApi } from '@/api/asset.api';
import { cn } from '@/lib/utils';

export function AddFundDialog() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const trimmed = query.trim();

  const searchQuery = useQuery({
    queryKey: ['tefas-search', trimmed],
    queryFn: () => assetApi.searchTefas(trimmed),
    enabled: open && trimmed.length >= 2,
    staleTime: 60 * 1000,
  });

  const importMutation = useMutation({
    mutationFn: (row: { code: string; type: 'YAT' | 'EMK' }) =>
      assetApi.importTefas(row.code, row.type),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['assets'] });
      void qc.invalidateQueries({ queryKey: ['portfolios'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      void qc.invalidateQueries({ queryKey: ['tefas-search'] });
    },
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setQuery('');
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          <Plus className="w-4 h-4 mr-2" />
          {t('prices.addFund')}
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>{t('prices.addFundTitle')}</DialogTitle>
          <DialogDescription>{t('prices.addFundDescription')}</DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <Input
            autoFocus
            placeholder={t('prices.addFundSearchPlaceholder')}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />

          <div className="border border-border rounded-md max-h-[360px] overflow-y-auto">
            {trimmed.length < 2 ? (
              <p className="text-xs text-muted-foreground p-4 text-center">
                {t('prices.addFundMinChars')}
              </p>
            ) : searchQuery.isFetching ? (
              <p className="text-xs text-muted-foreground p-4 text-center">
                {t('common.loading')}
              </p>
            ) : !searchQuery.data || searchQuery.data.length === 0 ? (
              <p className="text-xs text-muted-foreground p-4 text-center">
                {t('prices.addFundNoMatch')}
              </p>
            ) : (
              <ul>
                {searchQuery.data.map((f) => {
                  const isBusy =
                    importMutation.isPending && importMutation.variables?.code === f.code;
                  return (
                    <li
                      key={`${f.code}-${f.type}`}
                      className="flex items-center justify-between gap-3 px-3 py-2 border-b border-border/40 last:border-0 hover:bg-accent/30 transition-colors"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-sm font-medium">{f.code}</span>
                          <span
                            className={cn(
                              'text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded',
                              f.type === 'EMK'
                                ? 'bg-amber-500/15 text-amber-600 dark:text-amber-400'
                                : 'bg-muted text-muted-foreground',
                            )}
                          >
                            {f.type}
                          </span>
                        </div>
                        <p className="text-xs text-muted-foreground truncate">{f.name}</p>
                      </div>
                      {f.imported ? (
                        <span className="text-[11px] text-muted-foreground inline-flex items-center gap-1">
                          <Check className="w-3.5 h-3.5" />
                          {t('prices.addFundImported')}
                        </span>
                      ) : (
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={isBusy}
                          onClick={() => importMutation.mutate({ code: f.code, type: f.type })}
                        >
                          {isBusy ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          ) : (
                            <>
                              <Plus className="w-3.5 h-3.5 mr-1" />
                              {t('prices.addFundAdd')}
                            </>
                          )}
                        </Button>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
