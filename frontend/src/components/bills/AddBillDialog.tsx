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
import { Plus } from 'lucide-react';
import type { CreateBillRequest } from '@/types/bill.types';

interface AddBillDialogProps {
  onSubmit: (req: CreateBillRequest) => void;
  isPending: boolean;
}

export function AddBillDialog({ onSubmit, isPending }: AddBillDialogProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [amount, setAmount] = useState('');
  const [dueDay, setDueDay] = useState('1');
  const [category, setCategory] = useState('');

  const reset = () => {
    setName('');
    setAmount('');
    setDueDay('1');
    setCategory('');
  };

  const handleSubmit = () => {
    const parsed = parseFloat(amount);
    const day = parseInt(dueDay, 10);
    if (!name.trim() || !parsed || parsed <= 0 || day < 1 || day > 31) return;

    onSubmit({
      name: name.trim(),
      amount: parsed,
      dueDay: day,
      category: category || undefined,
      remindDaysBefore: 3,
      autoPay: false,
    });
    reset();
    setOpen(false);
  };

  return (
    <Dialog open={open} onOpenChange={(v) => { setOpen(v); if (!v) reset(); }}>
      <DialogTrigger asChild>
        <Button className="cursor-pointer">
          <Plus className="w-4 h-4 mr-2" />
          {t('bills.addBill')}
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[400px]">
        <DialogHeader>
          <DialogTitle>{t('bills.addBill')}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          <div className="space-y-1.5">
            <Label>{t('bills.billName')}</Label>
            <Input
              placeholder={t('bills.billNamePlaceholder')}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('budget.amount')}</Label>
              <div className="relative">
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="pr-10 font-mono tabular-nums"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                  TRY
                </span>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label>{t('bills.dueDay')}</Label>
              <Input
                type="number"
                min="1"
                max="31"
                value={dueDay}
                onChange={(e) => setDueDay(e.target.value)}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('budget.category')} <span className="text-muted-foreground">({t('common.optional')})</span></Label>
            <Input
              placeholder={t('bills.categoryPlaceholder')}
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            />
          </div>

          <Button
            onClick={handleSubmit}
            disabled={isPending || !name.trim() || !amount || parseFloat(amount) <= 0}
            className="w-full cursor-pointer"
          >
            {isPending ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
