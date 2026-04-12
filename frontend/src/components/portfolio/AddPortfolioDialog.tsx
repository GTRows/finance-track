import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useCreatePortfolio } from '@/hooks/usePortfolios';
import type { PortfolioType } from '@/types/portfolio.types';
import { Loader2, Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import { AxiosError } from 'axios';
import type { ApiError } from '@/types/auth.types';
import { PORTFOLIO_TYPES } from './portfolio-types';

interface AddPortfolioDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AddPortfolioDialog({ open, onOpenChange }: AddPortfolioDialogProps) {
  const { t } = useTranslation();
  const createPortfolio = useCreatePortfolio();

  const [name, setName] = useState('');
  const [type, setType] = useState<PortfolioType>('INDIVIDUAL');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setName('');
    setType('INDIVIDUAL');
    setDescription('');
    setError(null);
  };

  const handleClose = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (name.trim().length === 0) {
      setError(t('portfolio.nameRequired'));
      return;
    }

    try {
      await createPortfolio.mutateAsync({
        name: name.trim(),
        type,
        description: description.trim() || undefined,
      });
      handleClose(false);
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error || t('portfolio.failedToCreate'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[520px]">
        <DialogHeader>
          <DialogTitle>{t('portfolio.dialogTitle')}</DialogTitle>
          <DialogDescription>
            {t('portfolio.dialogDescription')}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="portfolio-name">{t('portfolio.nameLabel')}</Label>
            <Input
              id="portfolio-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('portfolio.namePlaceholder')}
              maxLength={100}
              autoFocus
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label>{t('portfolio.typeLabel')}</Label>
            <div className="grid grid-cols-4 gap-2">
              {PORTFOLIO_TYPES.map((opt) => {
                const Icon = opt.icon;
                const active = type === opt.value;
                const label = t(opt.labelKey);
                return (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => setType(opt.value)}
                    title={label}
                    className={cn(
                      'relative flex flex-col items-center gap-1.5 rounded-md border p-2.5 text-center transition-colors cursor-pointer',
                      active
                        ? 'border-primary bg-primary/5'
                        : 'border-input hover:border-muted-foreground/30 hover:bg-accent'
                    )}
                  >
                    {active && (
                      <span className="absolute top-1 right-1 w-3.5 h-3.5 rounded-full bg-primary flex items-center justify-center">
                        <Check className="w-2.5 h-2.5 text-primary-foreground" strokeWidth={3} />
                      </span>
                    )}
                    <div
                      className={cn(
                        'w-7 h-7 rounded-md flex items-center justify-center transition-colors',
                        active ? 'bg-primary/15 text-primary' : 'bg-muted text-muted-foreground'
                      )}
                    >
                      <Icon className="w-4 h-4" />
                    </div>
                    <span className="text-[11px] font-medium leading-tight">{label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="portfolio-description">{t('portfolio.descriptionLabel')}</Label>
            <Textarea
              id="portfolio-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t('portfolio.descriptionPlaceholder')}
              maxLength={1000}
              rows={3}
            />
          </div>

          {error && (
            <div className="rounded-md bg-destructive/10 border border-destructive/20 px-3 py-2.5">
              <p className="text-sm text-destructive">{error}</p>
            </div>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              className="cursor-pointer"
              onClick={() => handleClose(false)}
              disabled={createPortfolio.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button type="submit" className="cursor-pointer" disabled={createPortfolio.isPending}>
              {createPortfolio.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  {t('portfolio.creating')}
                </>
              ) : (
                t('portfolio.createPortfolio')
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
