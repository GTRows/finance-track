import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus, ArrowUp, ArrowDown } from 'lucide-react';
import { useAssets } from '@/hooks/useAssets';
import { cn } from '@/lib/utils';
import type { AlertDirection, CreateAlertRequest } from '@/types/alert.types';

interface AddAlertDialogProps {
  onSubmit: (req: CreateAlertRequest) => void;
  isPending: boolean;
}

export function AddAlertDialog({ onSubmit, isPending }: AddAlertDialogProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [assetId, setAssetId] = useState('');
  const [direction, setDirection] = useState<AlertDirection>('ABOVE');
  const [threshold, setThreshold] = useState('');

  const assetsQuery = useAssets();
  const assets = assetsQuery.data ?? [];

  const reset = () => {
    setAssetId('');
    setDirection('ABOVE');
    setThreshold('');
  };

  const parsedThreshold = parseFloat(threshold);
  const valid = assetId && parsedThreshold > 0;

  const handleSubmit = () => {
    if (!valid) return;
    onSubmit({ assetId, direction, thresholdTry: parsedThreshold });
    reset();
    setOpen(false);
  };

  return (
    <Dialog open={open} onOpenChange={(v) => { setOpen(v); if (!v) reset(); }}>
      <DialogTrigger asChild>
        <Button className="cursor-pointer">
          <Plus className="w-4 h-4 mr-2" />
          {t('alerts.add')}
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[420px]">
        <DialogHeader>
          <DialogTitle>{t('alerts.add')}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          <div className="space-y-1.5">
            <Label>{t('alerts.asset')}</Label>
            <select
              value={assetId}
              onChange={(e) => setAssetId(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm focus:outline-none focus:ring-1 focus:ring-ring"
            >
              <option value="">--</option>
              {assets.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.symbol} -- {a.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <Label>{t('alerts.direction.label')}</Label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setDirection('ABOVE')}
                className={cn(
                  'flex items-center justify-center gap-2 h-9 rounded-md border text-sm font-medium transition-colors cursor-pointer',
                  direction === 'ABOVE'
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-input text-muted-foreground hover:bg-accent'
                )}
              >
                <ArrowUp className="w-4 h-4" />
                {t('alerts.direction.above')}
              </button>
              <button
                type="button"
                onClick={() => setDirection('BELOW')}
                className={cn(
                  'flex items-center justify-center gap-2 h-9 rounded-md border text-sm font-medium transition-colors cursor-pointer',
                  direction === 'BELOW'
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-input text-muted-foreground hover:bg-accent'
                )}
              >
                <ArrowDown className="w-4 h-4" />
                {t('alerts.direction.below')}
              </button>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('alerts.threshold')}</Label>
            <div className="relative">
              <Input
                type="number"
                step="0.000001"
                min="0"
                placeholder="0.00"
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
                className="pr-10 font-mono tabular-nums"
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                TRY
              </span>
            </div>
          </div>

          <Button
            onClick={handleSubmit}
            disabled={isPending || !valid}
            className="w-full cursor-pointer"
          >
            {isPending ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
